package com.esporteclubepelotas.loja.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductFromInvoiceItemDto {

    @NotBlank(message = "Nome do produto é obrigatório")
    private String productName;

    private String categoryId;
    private String brand;

    @NotBlank(message = "SKU é obrigatório")
    private String sku;

    private String eanBarcode;

    @NotBlank(message = "Tamanho/variação é obrigatório")
    private String sizeLabel;

    private String colorLabel;

    @NotNull(message = "Preço de venda é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço de venda deve ser maior que zero")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.00")
    private BigDecimal memberPrice;
}
