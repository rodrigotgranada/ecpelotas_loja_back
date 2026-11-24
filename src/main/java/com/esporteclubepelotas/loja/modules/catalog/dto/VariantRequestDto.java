package com.esporteclubepelotas.loja.modules.catalog.dto;

import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantRequestDto {
    private String id;

    @NotBlank(message = "SKU é obrigatório")
    private String sku;

    private String eanBarcode;

    @NotBlank(message = "Tamanho/variação é obrigatório")
    private String sizeLabel;

    private String colorLabel;

    @NotNull(message = "Preço de custo é obrigatório")
    @DecimalMin(value = "0.00")
    private BigDecimal costPrice;

    @NotNull(message = "Preço de venda é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço de venda deve ser maior que zero")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.00")
    private BigDecimal memberPrice;

    @Min(0)
    private Integer minStock;

    private Boolean active;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantResponseDto {
        private String id;
        private String productId;
        private String productName;
        private String sku;
        private String eanBarcode;
        private String sizeLabel;
        private String colorLabel;
        private BigDecimal costPrice;
        private BigDecimal salePrice;
        private BigDecimal memberPrice;
        private int physicalStock;
        private int reservedStock;
        private int availableStock;
        private int minStock;
        private boolean lowStock;
        private boolean active;

        public static VariantResponseDto fromEntity(ProductVariant v) {
            return VariantResponseDto.builder()
                    .id(v.getId())
                    .productId(v.getProduct() != null ? v.getProduct().getId() : null)
                    .productName(v.getProduct() != null ? v.getProduct().getName() : null)
                    .sku(v.getSku())
                    .eanBarcode(v.getEanBarcode())
                    .sizeLabel(v.getSizeLabel())
                    .colorLabel(v.getColorLabel())
                    .costPrice(v.getCostPrice())
                    .salePrice(v.getSalePrice())
                    .memberPrice(v.getMemberPrice())
                    .physicalStock(v.getPhysicalStock())
                    .reservedStock(v.getReservedStock())
                    .availableStock(v.getAvailableStock())
                    .minStock(v.getMinStock())
                    .lowStock(v.getAvailableStock() <= v.getMinStock())
                    .active(v.isActive())
                    .build();
        }
    }
}
