package com.esporteclubepelotas.loja.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualStockAdjustmentDto {

    @NotBlank(message = "Informe a variação (SKU)")
    private String variantId;

    @NotNull(message = "Informe a variação de quantidade")
    private Integer quantityDelta;

    @NotBlank(message = "Motivo do ajuste é obrigatório")
    private String reason;
}
