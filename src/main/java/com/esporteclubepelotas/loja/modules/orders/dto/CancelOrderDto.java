package com.esporteclubepelotas.loja.modules.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderDto {
    private String portalUserId;
    private String cpf;
    private String reason;
}
