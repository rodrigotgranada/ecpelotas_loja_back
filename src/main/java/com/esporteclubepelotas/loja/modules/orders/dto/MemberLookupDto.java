package com.esporteclubepelotas.loja.modules.orders.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLookupDto {
    private boolean found;
    private String portalUserId;
    private String storeUserId;
    private String cpf;
    private String name;
    private boolean isSocioActive;
    private String socioPlanName;
    private String source;
}
