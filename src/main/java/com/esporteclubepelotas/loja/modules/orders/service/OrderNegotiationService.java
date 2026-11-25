package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.common.websocket.StoreWebSocketHandler;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import com.esporteclubepelotas.loja.modules.orders.dto.OrderResponseDto;
import com.esporteclubepelotas.loja.modules.orders.dto.UpdateOrderItemsDto;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderNegotiationService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("PENDING_WHATSAPP", "NEGOTIATING");

    private final StoreOrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreSettingsService settingsService;
    private final BusinessHoursTtlService businessHoursTtlService;
    private final WhatsappSummaryFormatterService summaryFormatterService;
    private final CustomerOrderService customerOrderService;
    private final StoreWebSocketHandler webSocketHandler;

    @Transactional
    public PageResponseDto<OrderResponseDto> listAdminOrders(String status, String search, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<String> idPage = orderRepository.findAdminOrderIds(
                cleanStatus,
                cleanSearch,
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "created_at"))
        );

        StoreSettings settings = settingsService.getOrCreateDefault();
        List<String> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PageResponseDto.<OrderResponseDto>builder()
                    .data(List.of()).total(0).page(safePage).limit(safeLimit).build();
        }

        Map<String, StoreOrder> map = orderRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(StoreOrder::getId, Function.identity()));

        List<OrderResponseDto> data = ids.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .map(o -> OrderResponseDto.fromEntity(o, settings.getWhatsappNumber(), false))
                .toList();

        return PageResponseDto.<OrderResponseDto>builder()
                .data(data)
                .total(idPage.getTotalElements())
                .page(safePage)
                .limit(safeLimit)
                .build();
    }

    @Transactional
    public OrderResponseDto getAdminOrder(String orderCode) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findOrderByCodeOrThrow(orderCode);
        return OrderResponseDto.fromEntity(order, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto startNegotiating(String orderCode, UserPrincipal actor) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findOrderByCodeOrThrow(orderCode);

        if ("PENDING_WHATSAPP".equals(order.getStatus())) {
            order.setStatus("NEGOTIATING");
            order.setSellerUserId(actor.getId());
            order.setSellerName(actor.getName());
            order.getEvents().add(OrderEvent.builder()
                    .order(order)
                    .eventType("NEGOTIATION_STARTED")
                    .actorId(actor.getId())
                    .actorName(actor.getName())
                    .description("Atendimento assumido no PDV.")
                    .build());
            order = orderRepository.save(order);
            webSocketHandler.broadcastAfterCommit("orders", "UPDATED", order.getOrderCode());
        }
        return OrderResponseDto.fromEntity(order, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto updateOrderItems(String orderCode, UpdateOrderItemsDto dto, UserPrincipal actor) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findOrderByCodeOrThrow(orderCode);

        if (!EDITABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("Pedido no status " + order.getStatus() + " não pode mais ser editado.");
        }

        Map<String, Integer> oldQtyMap = new LinkedHashMap<>();
        for (OrderItem existing : order.getItems()) {
            oldQtyMap.merge(existing.getVariantId(), existing.getQuantity(), Integer::sum);
        }

        Map<String, Integer> newQtyMap = new LinkedHashMap<>();
        for (UpdateOrderItemsDto.OrderLineInputDto line : dto.getItems()) {
            newQtyMap.merge(line.getVariantId().trim(), line.getQuantity(), Integer::sum);
        }

        Set<String> allVariantIds = new TreeSet<>();
        allVariantIds.addAll(oldQtyMap.keySet());
        allVariantIds.addAll(newQtyMap.keySet());

        Map<String, ProductVariant> lockedMap = variantRepository.findAllByIdInForUpdateOrdered(allVariantIds)
                .stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        if (lockedMap.size() != allVariantIds.size()) {
            throw new ResourceNotFoundException("Uma ou mais variações (SKU) informadas não foram encontradas.");
        }

        for (String variantId : allVariantIds) {
            ProductVariant variant = lockedMap.get(variantId);
            int delta = newQtyMap.getOrDefault(variantId, 0) - oldQtyMap.getOrDefault(variantId, 0);
            if (delta > 0) {
                if (!variant.isActive() || !variant.getProduct().isActive()) {
                    throw new BusinessException("Produto inativo: " + variant.getProduct().getName());
                }
                if (variant.getAvailableStock() < delta) {
                    throw new BusinessException(
                            "Estoque disponível insuficiente para " + variant.getProduct().getName()
                                    + " (" + variant.getSizeLabel() + "). Disponível: " + variant.getAvailableStock()
                    );
                }
            }
            variant.setReservedStock(Math.max(0, variant.getReservedStock() + delta));
            variantRepository.save(variant);
        }

        order.getItems().clear();
        BigDecimal rawSubtotal = BigDecimal.ZERO;
        BigDecimal itemsAfterSocio = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : newQtyMap.entrySet()) {
            ProductVariant variant = lockedMap.get(entry.getKey());
            int qty = entry.getValue();
            BigDecimal unitPrice = variant.getSalePrice();
            BigDecimal appliedPrice = resolveItemPrice(variant, order.isSocioDiscountApplied());

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

        BigDecimal socioSavings = rawSubtotal.subtract(itemsAfterSocio);
        order.setSubtotalAmount(rawSubtotal);
        order.setDiscountAmount(socioSavings.add(extraDiscount));
        order.setTotalAmount(itemsAfterSocio.subtract(extraDiscount));
        if (dto.getNotes() != null) {
            order.setNotes(dto.getNotes().trim());
        }
        order.setStatus("NEGOTIATING");
        order.setSellerUserId(actor.getId());
        order.setSellerName(actor.getName());
        order.setWhatsappSummaryText(summaryFormatterService.buildSummaryText(order));
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("ITEMS_EDITED")
                .actorId(actor.getId())
                .actorName(actor.getName())
                .description("Itens/desconto editados no PDV (" + order.getItems().size() + " item(ns), total R$ " + order.getTotalAmount() + ").")
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "UPDATED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto reactivateExpiredOrder(String orderCode, UserPrincipal actor) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findOrderByCodeOrThrow(orderCode);

        if (!"EXPIRED".equals(order.getStatus())) {
            throw new BusinessException("Apenas reservas expiradas podem ser reativadas.");
        }

        Set<String> variantIds = order.getItems().stream()
                .map(OrderItem::getVariantId)
                .collect(Collectors.toCollection(TreeSet::new));

        Map<String, ProductVariant> locked = variantRepository.findAllByIdInForUpdateOrdered(variantIds)
                .stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        for (OrderItem item : order.getItems()) {
            ProductVariant variant = locked.get(item.getVariantId());
            if (variant == null || !variant.isActive() || variant.getAvailableStock() < item.getQuantity()) {
                throw new BusinessException(
                        "Não há estoque suficiente para reativar " + item.getProductName() + " (" + item.getSizeLabel() + ")."
                );
            }
            variant.setReservedStock(variant.getReservedStock() + item.getQuantity());
            variantRepository.save(variant);
        }

        order.setStatus("NEGOTIATING");
        order.setExpiresAt(businessHoursTtlService.calculateExpiresAt(Instant.now(), settings));
        order.setSellerUserId(actor.getId());
        order.setSellerName(actor.getName());
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("REACTIVATED")
                .actorId(actor.getId())
                .actorName(actor.getName())
                .description("Reserva expirada reativada pelo atendimento.")
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "UPDATED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto cancelByStaff(String orderCode, String reason, UserPrincipal actor) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findOrderByCodeOrThrow(orderCode);

        if (!EDITABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("Pedido no status " + order.getStatus() + " não pode ser cancelado por aqui.");
        }

        customerOrderService.releaseReservedStock(order);
        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        order.setCancelledReason(reason != null && !reason.isBlank() ? reason.trim() : "Cancelado pela Loja");
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("CANCELLED_BY_STAFF")
                .actorId(actor.getId())
                .actorName(actor.getName())
                .description(order.getCancelledReason())
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "CANCELLED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), true);
    }

    public StoreOrder findOrderByCodeOrThrow(String rawCode) {
        String trimmed = rawCode != null ? rawCode.trim().toUpperCase() : "";
        String normalized = trimmed.startsWith("#") ? trimmed : "#" + trimmed;
        return orderRepository.findWithItemsByOrderCode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + normalized));
    }

    private BigDecimal resolveItemPrice(ProductVariant variant, boolean isSocio) {
        if (isSocio
                && variant.getMemberPrice() != null
                && variant.getMemberPrice().compareTo(BigDecimal.ZERO) > 0
                && variant.getMemberPrice().compareTo(variant.getSalePrice()) < 0) {
            return variant.getMemberPrice();
        }
        return variant.getSalePrice();
    }
}
