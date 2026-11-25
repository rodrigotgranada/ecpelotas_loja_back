package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.common.websocket.StoreWebSocketHandler;
import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import com.esporteclubepelotas.loja.modules.auth.service.PortalIntegrationClient;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import com.esporteclubepelotas.loja.modules.orders.dto.CreateWhatsappReservationDto;
import com.esporteclubepelotas.loja.modules.orders.dto.OrderResponseDto;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderReservationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreUserRepository storeUserRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreOrderRepository orderRepository;
    private final StoreSettingsService settingsService;
    private final BusinessHoursTtlService businessHoursTtlService;
    private final WhatsappSummaryFormatterService summaryFormatterService;
    private final PortalIntegrationClient portalIntegrationClient;
    private final StoreWebSocketHandler webSocketHandler;

    @Transactional
    public OrderResponseDto createWhatsappReservation(CreateWhatsappReservationDto request, UserPrincipal principal) {
        StoreUser user = storeUserRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        String cleanCpf = user.getCpf() != null ? user.getCpf().replaceAll("[^0-9]", "") : "";
        if (cleanCpf.length() != 11) {
            throw new BusinessException("Cadastre um CPF válido no Portal antes de realizar reservas online.");
        }

        StoreSettings settings = settingsService.getOrCreateDefault();
        long openReservations = orderRepository.countActiveReservationsForUser(user.getId(), user.getPortalUserId());
        if (openReservations >= settings.getMaxOpenReservationsPerUser()) {
            throw new BusinessException(
                    "Você já possui " + openReservations + " reserva(s) em aberto. Conclua ou cancele antes de abrir outra."
            );
        }

        boolean isSocioActive = resolveAndCacheSocioStatus(user, cleanCpf);
        Map<String, Integer> requestedQuantities = consolidateQuantities(request.getItems());

        List<ProductVariant> lockedVariants = variantRepository.findAllByIdInForUpdateOrdered(requestedQuantities.keySet());
        if (lockedVariants.size() != requestedQuantities.size()) {
            throw new ResourceNotFoundException("Um ou mais itens selecionados não existem mais no catálogo.");
        }

        Map<String, ProductVariant> variantMap = lockedVariants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        StoreOrder order = StoreOrder.builder()
                .orderCode(generateUniqueOrderCode())
                .channel("WHATSAPP")
                .status("PENDING_WHATSAPP")
                .customerType("REGISTERED")
                .storeUserId(user.getId())
                .portalUserId(user.getPortalUserId())
                .customerName(user.getFullName())
                .customerWhatsapp(request.getCustomerWhatsapp().trim())
                .customerCpf(cleanCpf)
                .isSocioDiscountApplied(isSocioActive)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .expiresAt(businessHoursTtlService.calculateExpiresAt(Instant.now(), settings))
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            ProductVariant variant = variantMap.get(entry.getKey());
            int qty = entry.getValue();

            if (!variant.isActive() || !variant.getProduct().isActive()) {
                throw new BusinessException("Produto indisponível no catálogo: " + variant.getProduct().getName());
            }
            if (variant.getAvailableStock() < qty) {
                throw new BusinessException(
                        "Estoque insuficiente para " + variant.getProduct().getName()
                                + " (" + variant.getSizeLabel() + "). Disponível: " + variant.getAvailableStock()
                );
            }

            variant.setReservedStock(variant.getReservedStock() + qty);
            variantRepository.save(variant);

            BigDecimal unitPrice = variant.getSalePrice();
            BigDecimal appliedPrice = unitPrice;
            if (isSocioActive
                    && variant.getMemberPrice() != null
                    && variant.getMemberPrice().compareTo(BigDecimal.ZERO) > 0
                    && variant.getMemberPrice().compareTo(unitPrice) < 0) {
                appliedPrice = variant.getMemberPrice();
            }

            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            BigDecimal lineTotal = appliedPrice.multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(lineSubtotal);
            total = total.add(lineTotal);

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

        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(subtotal.subtract(total));
        order.setTotalAmount(total);
        order.setWhatsappSummaryText(summaryFormatterService.buildSummaryText(order));
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("CREATED_WHATSAPP_RESERVATION")
                .actorId(user.getId())
                .actorName(user.getFullName())
                .description("Reserva criada na vitrine pública com " + order.getItems().size() + " item(ns).")
                .build());

        StoreOrder saved = orderRepository.save(order);
        webSocketHandler.broadcastAfterCommit("orders", "CREATED", saved.getOrderCode());
        return OrderResponseDto.fromEntity(saved, settings.getWhatsappNumber(), false);
    }

    private boolean resolveAndCacheSocioStatus(StoreUser user, String cleanCpf) {
        Optional<PortalIntegrationClient.MemberStatusDto> liveStatus = portalIntegrationClient.checkMemberStatus(cleanCpf);
        if (liveStatus.isPresent()) {
            PortalIntegrationClient.MemberStatusDto dto = liveStatus.get();
            boolean active = dto.isFound() && dto.isSocioActive();
            user.setSocioActive(active);
            user.setSocioPlanName(dto.getSocioPlanName());
            storeUserRepository.save(user);
            return active;
        }
        return user.isSocioActive();
    }

    private Map<String, Integer> consolidateQuantities(List<CreateWhatsappReservationDto.ReservationLineDto> items) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (CreateWhatsappReservationDto.ReservationLineDto item : items) {
            map.merge(item.getVariantId().trim(), item.getQuantity(), Integer::sum);
        }
        return map;
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
