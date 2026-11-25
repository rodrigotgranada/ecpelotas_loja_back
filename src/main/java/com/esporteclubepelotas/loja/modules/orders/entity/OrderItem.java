package com.esporteclubepelotas.loja.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Column(name = "variant_id", nullable = false, length = 36)
    private String variantId;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(name = "size_label", nullable = false, length = 40)
    private String sizeLabel;

    @Column(name = "color_label", length = 60)
    private String colorLabel;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "applied_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
