package com.esporteclubepelotas.loja.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDocument {

    public enum DocType {
        INBOUND_INVOICE,
        MANUAL_ENTRY,
        INVENTORY_ADJUSTMENT
    }

    public enum Status {
        DRAFT,
        POSTED,
        CANCELLED
    }

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private DocType docType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;

    @Column(name = "invoice_series", length = 15)
    private String invoiceSeries;

    @Column(name = "access_key_44", unique = true, length = 44)
    private String accessKey44;

    @Column(name = "supplier_cnpj", length = 20)
    private String supplierCnpj;

    @Column(name = "supplier_name", length = 160)
    private String supplierName;

    @Column(name = "xml_url", columnDefinition = "TEXT")
    private String xmlUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "total_cost", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "created_by_user_id", length = 36)
    private String createdByUserId;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    @BatchSize(size = 50)
    private List<StockDocumentItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.totalCost == null) this.totalCost = BigDecimal.ZERO;
    }
}
