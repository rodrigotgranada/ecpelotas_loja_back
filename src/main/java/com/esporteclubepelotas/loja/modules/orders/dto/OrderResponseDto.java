package com.esporteclubepelotas.loja.modules.orders.dto;

import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderPayment;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {
    private String id;
    private String orderCode;
    private String channel;
    private String status;
    private String customerType;
    private String customerName;
    private String customerWhatsapp;
    private String customerCpf;
    private boolean isSocioDiscountApplied;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String notes;
    private String whatsappSummaryText;
    private String whatsappUrl;
    private Instant expiresAt;
    private Instant confirmedAt;
    private Instant cancelledAt;
    private String cancelledReason;
    private Instant createdAt;
    private List<OrderItemDto> items;
    private List<OrderPaymentDto> payments;
    private List<OrderEventDto> events;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private String id;
        private String variantId;
        private String productName;
        private String sku;
        private String sizeLabel;
        private String colorLabel;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal appliedPrice;
        private BigDecimal lineTotal;

        public static OrderItemDto fromEntity(OrderItem item) {
            return OrderItemDto.builder()
                    .id(item.getId())
                    .variantId(item.getVariantId())
                    .productName(item.getProductName())
                    .sku(item.getSku())
                    .sizeLabel(item.getSizeLabel())
                    .colorLabel(item.getColorLabel())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .appliedPrice(item.getAppliedPrice())
                    .lineTotal(item.getLineTotal())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPaymentDto {
        private String id;
        private String paymentMethod;
        private BigDecimal amount;

        public static OrderPaymentDto fromEntity(OrderPayment p) {
            return OrderPaymentDto.builder()
                    .id(p.getId())
                    .paymentMethod(p.getPaymentMethod())
                    .amount(p.getAmount())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderEventDto {
        private String id;
        private String eventType;
        private String actorName;
        private String description;
        private Instant createdAt;

        public static OrderEventDto fromEntity(OrderEvent e) {
            return OrderEventDto.builder()
                    .id(e.getId())
                    .eventType(e.getEventType())
                    .actorName(e.getActorName())
                    .description(e.getDescription())
                    .createdAt(e.getCreatedAt())
                    .build();
        }
    }

    public static OrderResponseDto fromEntity(StoreOrder order, String storeWhatsappNumber, boolean includeEvents) {
        String cleanPhone = storeWhatsappNumber != null ? storeWhatsappNumber.replaceAll("[^0-9]", "") : "";
        String waUrl = null;
        if (!cleanPhone.isEmpty() && order.getWhatsappSummaryText() != null) {
            waUrl = "https://wa.me/" + cleanPhone + "?text="
                    + URLEncoder.encode(order.getWhatsappSummaryText(), StandardCharsets.UTF_8);
        }

        return OrderResponseDto.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .channel(order.getChannel())
                .status(order.getStatus())
                .customerType(order.getCustomerType())
                .customerName(order.getCustomerName())
                .customerWhatsapp(order.getCustomerWhatsapp())
                .customerCpf(order.getCustomerCpf())
                .isSocioDiscountApplied(order.isSocioDiscountApplied())
                .subtotalAmount(order.getSubtotalAmount())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .whatsappSummaryText(order.getWhatsappSummaryText())
                .whatsappUrl(waUrl)
                .expiresAt(order.getExpiresAt())
                .confirmedAt(order.getConfirmedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelledReason(order.getCancelledReason())
                .createdAt(order.getCreatedAt())
                .items(order.getItems().stream().map(OrderItemDto::fromEntity).toList())
                .payments(order.getPayments().stream().map(OrderPaymentDto::fromEntity).toList())
                .events(includeEvents ? order.getEvents().stream().map(OrderEventDto::fromEntity).toList() : List.of())
                .build();
    }
}
