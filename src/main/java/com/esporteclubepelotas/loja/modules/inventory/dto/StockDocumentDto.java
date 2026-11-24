package com.esporteclubepelotas.loja.modules.inventory.dto;

import com.esporteclubepelotas.loja.modules.inventory.entity.StockDocument;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockDocumentItem;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDocumentDto {
    private String id;
    private String docType;
    private String status;
    private String invoiceNumber;
    private String invoiceSeries;
    private String accessKey44;
    private String supplierCnpj;
    private String supplierName;
    private String notes;
    private BigDecimal totalCost;
    private Instant postedAt;
    private Instant createdAt;
    private boolean allItemsLinked;
    private List<StockDocumentItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockDocumentItemDto {
        private String id;
        private int lineNumber;
        private String supplierProductCode;
        private String eanBarcode;
        private String supplierProductName;
        private String ncm;
        private int quantity;
        private BigDecimal unitCost;
        private BigDecimal totalCost;
        private String linkedVariantId;
        private String linkedVariantSku;
        private String linkedProductId;
        private String linkedProductName;
        private String linkedSizeLabel;

        public static StockDocumentItemDto fromEntity(StockDocumentItem item) {
            return StockDocumentItemDto.builder()
                    .id(item.getId())
                    .lineNumber(item.getLineNumber())
                    .supplierProductCode(item.getSupplierProductCode())
                    .eanBarcode(item.getEanBarcode())
                    .supplierProductName(item.getSupplierProductName())
                    .ncm(item.getNcm())
                    .quantity(item.getQuantity())
                    .unitCost(item.getUnitCost())
                    .totalCost(item.getTotalCost())
                    .linkedVariantId(item.getVariant() != null ? item.getVariant().getId() : null)
                    .linkedVariantSku(item.getVariant() != null ? item.getVariant().getSku() : null)
                    .linkedProductId(item.getVariant() != null && item.getVariant().getProduct() != null
                            ? item.getVariant().getProduct().getId() : null)
                    .linkedProductName(item.getVariant() != null && item.getVariant().getProduct() != null
                            ? item.getVariant().getProduct().getName() : null)
                    .linkedSizeLabel(item.getVariant() != null ? item.getVariant().getSizeLabel() : null)
                    .build();
        }
    }

    public static StockDocumentDto fromEntity(StockDocument doc, boolean includeItems) {
        List<StockDocumentItemDto> itemDtos = includeItems && doc.getItems() != null
                ? doc.getItems().stream().map(StockDocumentItemDto::fromEntity).toList()
                : List.of();
        boolean allLinked = doc.getItems() != null
                && !doc.getItems().isEmpty()
                && doc.getItems().stream().allMatch(i -> i.getVariant() != null);

        return StockDocumentDto.builder()
                .id(doc.getId())
                .docType(doc.getDocType().name())
                .status(doc.getStatus().name())
                .invoiceNumber(doc.getInvoiceNumber())
                .invoiceSeries(doc.getInvoiceSeries())
                .accessKey44(doc.getAccessKey44())
                .supplierCnpj(doc.getSupplierCnpj())
                .supplierName(doc.getSupplierName())
                .notes(doc.getNotes())
                .totalCost(doc.getTotalCost())
                .postedAt(doc.getPostedAt())
                .createdAt(doc.getCreatedAt())
                .allItemsLinked(allLinked)
                .items(itemDtos)
                .build();
    }
}
