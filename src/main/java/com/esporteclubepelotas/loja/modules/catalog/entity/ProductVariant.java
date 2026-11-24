package com.esporteclubepelotas.loja.modules.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(name = "ean_barcode", length = 64)
    private String eanBarcode;

    @Column(name = "size_label", nullable = false, length = 40)
    private String sizeLabel;

    @Column(name = "color_label", length = 60)
    private String colorLabel;

    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Column(name = "member_price", precision = 12, scale = 2)
    private BigDecimal memberPrice;

    @Column(name = "physical_stock", nullable = false)
    @Builder.Default
    private int physicalStock = 0;

    @Column(name = "reserved_stock", nullable = false)
    @Builder.Default
    private int reservedStock = 0;

    @Column(name = "min_stock", nullable = false)
    @Builder.Default
    private int minStock = 2;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public int getAvailableStock() {
        return Math.max(0, this.physicalStock - this.reservedStock);
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.costPrice == null) this.costPrice = BigDecimal.ZERO;
        if (this.salePrice == null) this.salePrice = BigDecimal.ZERO;
    }
}
