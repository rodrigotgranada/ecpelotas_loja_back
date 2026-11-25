package com.esporteclubepelotas.loja.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrder {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "order_code", nullable = false, unique = true, length = 24)
    private String orderCode;

    @Column(nullable = false, length = 30)
    private String channel;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "customer_type", nullable = false, length = 30)
    private String customerType;

    @Column(name = "store_user_id", length = 36)
    private String storeUserId;

    @Column(name = "portal_user_id", length = 36)
    private String portalUserId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_whatsapp", length = 30)
    private String customerWhatsapp;

    @Column(name = "customer_cpf", length = 20)
    private String customerCpf;

    @Column(name = "is_socio_discount_applied", nullable = false)
    @Builder.Default
    private boolean isSocioDiscountApplied = false;

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "whatsapp_summary_text", columnDefinition = "TEXT")
    private String whatsappSummaryText;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_reason", length = 255)
    private String cancelledReason;

    @Column(name = "seller_user_id", length = 36)
    private String sellerUserId;

    @Column(name = "seller_name", length = 150)
    private String sellerName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OrderPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OrderEvent> events = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
