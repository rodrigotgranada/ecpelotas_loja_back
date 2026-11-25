package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.common.websocket.StoreWebSocketHandler;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockMovement;
import com.esporteclubepelotas.loja.modules.inventory.service.StockLedgerService;
import com.esporteclubepelotas.loja.modules.orders.dto.*;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCheckoutService {

    private static final Set<String> CONFIRMABLE_STATUSES = Set.of("PENDING_WHATSAPP", "NEGOTIATING");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreOrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final StockLedgerService stockLedgerService;
    private final StoreSettingsService settingsService;
    private final OrderPaymentValidatorService paymentValidatorService;
    private final OrderNegotiationService orderNegotiationService;
    private final MemberLookupService memberLookupService;
    private final StoreWebSocketHandler webSocketHandler;

    @Transactional
    public OrderResponseDto confirmWhatsappOrderSale(
            String orderCode,
            ConfirmOrderPaymentDto dto,
            UserPrincipal actor
    ) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = orderNegotiationService.findOrderByCodeOrThrow(orderCode);

        if (!CONFIRMABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("Pedido no status " + order.getStatus() + " não pode ser confirmado.");
        }

        order.getPayments().clear();
        order.getPayments().addAll(paymentValidatorService.buildAndValidatePayments(order, dto.getPayments()));

        Set<String> variantIds = order.getItems().stream()
                .map(OrderItem::getVariantId)
                .collect(Collectors.toCollection(TreeSet::new));

        Map<String, ProductVariant> locked = variantRepository.findAllByIdInForUpdateOrdered(variantIds)
                .stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        for (OrderItem item : order.getItems()) {
            ProductVariant variant = locked.get(item.getVariantId());
            if (variant == null) {
                throw new ResourceNotFoundException("Variação não encontrada: " + item.getSku());
            }
            variant.setReservedStock(Math.max(0, variant.getReservedStock() - item.getQuantity()));
            stockLedgerService.appendMovement(
                    variant,
                    null,
                    order.getId(),
                    StockMovement.MovementType.SALE_OUTBOUND,
                    -item.getQuantity(),
                    variant.getCostPrice(),
                    "Venda confirmada " + order.getOrderCode(),
                    actor.getId(),
                    actor.getName()
            );
            variantRepository.save(variant);
        }

        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            order.setNotes(dto.getNotes().trim());
        }
        order.setStatus("CONFIRMED_PAID");
        order.setConfirmedAt(Instant.now());
        order.setSellerUserId(actor.getId());
        order.setSellerName(actor.getName());
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("SALE_CONFIRMED")
                .actorId(actor.getId())
                .actorName(actor.getName())
                .description("Venda confirmada e baixada no estoque (" + order.getPayments().size() + " forma(s) de pagamento).")
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "CONFIRMED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto createCounterSale(CreateCounterSaleDto dto, UserPrincipal actor) {
        StoreSettings settings = settingsService.getOrCreateDefault();

        String cleanCpf = dto.getCustomerCpf() != null ? dto.getCustomerCpf().replaceAll("[^0-9]", "") : "";
        boolean hasCpf = cleanCpf.length() == 11;

        MemberLookupDto member = hasCpf ? memberLookupService.lookupByCpf(cleanCpf) : null;
        boolean isSocio = member != null && member.isSocioActive();
        String customerType = hasCpf ? "REGISTERED" : "WALK_IN_ANONYMOUS";

        String resolvedName = (dto.getCustomerName() != null && !dto.getCustomerName().isBlank())
                ? dto.getCustomerName().trim()
                : (member != null && member.isFound() && member.getName() != null
                        ? member.getName()
                        : "Consumidor Final / Balcão");

        Map<String, Integer> requestedQty = new LinkedHashMap<>();
        for (UpdateOrderItemsDto.OrderLineInputDto line : dto.getItems()) {
            requestedQty.merge(line.getVariantId().trim(), line.getQuantity(), Integer::sum);
        }

        List<ProductVariant> lockedList = variantRepository.findAllByIdInForUpdateOrdered(requestedQty.keySet());
        if (lockedList.size() != requestedQty.size()) {
            throw new ResourceNotFoundException("Uma ou mais variações (SKU) não encontradas.");
        }
        Map<String, ProductVariant> lockedMap = lockedList.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        StoreOrder order = StoreOrder.builder()
                .id(UUID.randomUUID().toString())
                .orderCode(generateUniqueOrderCode())
                .channel("STORE_COUNTER")
                .status("CONFIRMED_PAID")
                .customerType(customerType)
                .storeUserId(member != null ? member.getStoreUserId() : null)
                .portalUserId(member != null ? member.getPortalUserId() : null)
                .customerName(resolvedName)
                .customerCpf(hasCpf ? cleanCpf : null)
                .isSocioDiscountApplied(isSocio)
                .notes(dto.getNotes() != null ? dto.getNotes().trim() : null)
                .confirmedAt(Instant.now())
                .sellerUserId(actor.getId())
                .sellerName(actor.getName())
                .build();

        BigDecimal rawSubtotal = BigDecimal.ZERO;
        BigDecimal itemsAfterSocio = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : requestedQty.entrySet()) {
            ProductVariant variant = lockedMap.get(entry.getKey());
            int qty = entry.getValue();

            if (!variant.isActive() || !variant.getProduct().isActive()) {
                throw new BusinessException("Produto inativo: " + variant.getProduct().getName());
            }
            if (variant.getAvailableStock() < qty) {
                throw new BusinessException(
                        "Estoque disponível insuficiente para " + variant.getProduct().getName()
                                + " (" + variant.getSizeLabel() + "). Disponível: " + variant.getAvailableStock()
                );
            }

            stockLedgerService.appendMovement(
                    variant,
                    null,
                    order.getId(),
                    StockMovement.MovementType.SALE_OUTBOUND,
                    -qty,
                    variant.getCostPrice(),
                    "Venda Balcão " + order.getOrderCode(),
                    actor.getId(),
                    actor.getName()
            );
            variantRepository.save(variant);

            BigDecimal unitPrice = variant.getSalePrice();
            BigDecimal appliedPrice = unitPrice;
            if (isSocio
                    && variant.getMemberPrice() != null
                    && variant.getMemberPrice().compareTo(BigDecimal.ZERO) > 0
                    && variant.getMemberPrice().compareTo(unitPrice) < 0) {
                appliedPrice = variant.getMemberPrice();
            }

            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            BigDecimal lineTotal = appliedPrice.multiply(BigDecimal.valueOf(qty));
            rawSubtotal = rawSubtotal.add(lineSubtotal);
            itemsAfterSocio = itemsAfterSocio.add(lineTotal);

            order.getItems().add(OrderItem.builder()
                    .order(order)
                    .variantId(variant.getId())
                    .productName(variant.getProduct().getName())
                    .sku(variant.getSku())
                    .sizeLabel(variant.getSizeLabel())
                    .colorLabel(variant.getColorLabel())
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .appliedPrice(appliedPrice)
                    .lineTotal(lineTotal)
                    .build());
        }

        BigDecimal extraDiscount = dto.getDiscountAmount() != null
                ? dto.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (extraDiscount.compareTo(itemsAfterSocio) > 0) {
            extraDiscount = itemsAfterSocio;
        }

        order.setSubtotalAmount(rawSubtotal);
        order.setDiscountAmount(rawSubtotal.subtract(itemsAfterSocio).add(extraDiscount));
        order.setTotalAmount(itemsAfterSocio.subtract(extraDiscount));
        order.getPayments().addAll(paymentValidatorService.buildAndValidatePayments(order, dto.getPayments()));
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("COUNTER_SALE_CONFIRMED")
                .actorId(actor.getId())
                .actorName(actor.getName())
                .description("Venda direta no Balcão concluída (" + customerType + ").")
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "CREATED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), true);
    }

    private String generateUniqueOrderCode() {
        for (int attempt = 0; attempt < 15; attempt++) {
            int num = 1000 + RANDOM.nextInt(9000);
            String candidate = "#ALC-" + num;
            if (!orderRepository.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        return "#ALC-" + (10000 + RANDOM.nextInt(89999));
    }
}
