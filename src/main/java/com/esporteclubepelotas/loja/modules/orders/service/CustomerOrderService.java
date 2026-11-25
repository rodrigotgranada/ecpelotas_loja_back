package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ForbiddenException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import com.esporteclubepelotas.loja.modules.orders.dto.OrderResponseDto;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING_WHATSAPP", "NEGOTIATING");

    private final StoreOrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreSettingsService settingsService;

    @Transactional
    public List<OrderResponseDto> listOrdersForCustomer(String storeUserId, String portalUserId, String cpf) {
        String cleanCpf = normalizeCpf(cpf);
        StoreSettings settings = settingsService.getOrCreateDefault();
        return orderRepository.findCustomerOrders(storeUserId, portalUserId, cleanCpf).stream()
                .map(order -> OrderResponseDto.fromEntity(order, settings.getWhatsappNumber(), false))
                .toList();
    }

    @Transactional
    public OrderResponseDto getOrderForCustomer(String orderCode, String storeUserId, String portalUserId, String cpf) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findAndVerifyOwnership(orderCode, storeUserId, portalUserId, normalizeCpf(cpf));
        return OrderResponseDto.fromEntity(order, settings.getWhatsappNumber(), true);
    }

    @Transactional
    public OrderResponseDto cancelOrderForCustomer(
            String orderCode,
            String storeUserId,
            String portalUserId,
            String cpf,
            String reason
    ) {
        StoreSettings settings = settingsService.getOrCreateDefault();
        StoreOrder order = findAndVerifyOwnership(orderCode, storeUserId, portalUserId, normalizeCpf(cpf));

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessException("Este pedido não pode mais ser cancelado (status: " + order.getStatus() + ").");
        }

        releaseReservedStock(order);
        order.setStatus("CANCELLED");
        order.setCancelledAt(Instant.now());
        order.setCancelledReason(reason != null && !reason.isBlank() ? reason.trim() : "Cancelado pelo torcedor");
        order.getEvents().add(OrderEvent.builder()
                .order(order)
                .eventType("CANCELLED_BY_CUSTOMER")
                .actorId(storeUserId != null ? storeUserId : portalUserId)
                .actorName(order.getCustomerName())
                .description(order.getCancelledReason())
                .build());

        return OrderResponseDto.fromEntity(orderRepository.save(order), settings.getWhatsappNumber(), true);
    }

    public void releaseReservedStock(StoreOrder order) {
        Set<String> variantIds = order.getItems().stream()
                .map(OrderItem::getVariantId)
                .collect(Collectors.toSet());
        if (variantIds.isEmpty()) return;

        Map<String, ProductVariant> locked = variantRepository.findAllByIdInForUpdateOrdered(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        for (OrderItem item : order.getItems()) {
            ProductVariant variant = locked.get(item.getVariantId());
            if (variant != null) {
                variant.setReservedStock(Math.max(0, variant.getReservedStock() - item.getQuantity()));
                variantRepository.save(variant);
            }
        }
    }

    private StoreOrder findAndVerifyOwnership(String rawOrderCode, String storeUserId, String portalUserId, String cleanCpf) {
        String normalizedCode = normalizeOrderCode(rawOrderCode);
        StoreOrder order = orderRepository.findWithItemsByOrderCode(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + normalizedCode));

        boolean sameStoreUser = storeUserId != null && Objects.equals(storeUserId, order.getStoreUserId());
        boolean samePortalUser = portalUserId != null && Objects.equals(portalUserId, order.getPortalUserId());
        boolean sameCpf = cleanCpf != null && Objects.equals(cleanCpf, order.getCustomerCpf());

        if (!sameStoreUser && !samePortalUser && !sameCpf) {
            throw new ForbiddenException("Você não tem permissão para acessar este pedido.");
        }
        return order;
    }

    private String normalizeOrderCode(String code) {
        String trimmed = code != null ? code.trim().toUpperCase() : "";
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    private String normalizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String digits = cpf.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
