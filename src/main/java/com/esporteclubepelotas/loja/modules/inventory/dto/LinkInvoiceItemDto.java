package com.esporteclubepelotas.loja.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkInvoiceItemDto {
    @NotBlank(message = "Informe a variação para vincular")
    private String variantId;
}
