package com.esporteclubepelotas.loja.modules.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderItemsDto {

    @Valid
    @NotEmpty(message = "O pedido precisa manter pelo menos 1 item")
    private List<OrderLineInputDto> items;

    @DecimalMin(value = "0.00")
    private BigDecimal discountAmount;

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderLineInputDto {
        @NotBlank(message = "Informe a variação (SKU)")
        private String variantId;

        @Min(value = 1, message = "Quantidade mínima é 1")
        private int quantity;
    }
}
