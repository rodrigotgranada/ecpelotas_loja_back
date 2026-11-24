package com.esporteclubepelotas.loja.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortalUserSyncDto {
    private String portalUserId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String cpf;
    private String role;
    @JsonProperty("socioActive")
    private boolean socioActive;
    private String socioPlanName;
    private String socioStatus;
}
