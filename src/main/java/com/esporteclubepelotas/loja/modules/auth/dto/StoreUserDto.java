package com.esporteclubepelotas.loja.modules.auth.dto;

import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreUserDto {
    private String id;
    private String portalUserId;
    private String fullName;
    private String email;
    private String cpf;
    private String role;
    private String authSource;
    private boolean isSocioActive;
    private String socioPlanName;
    private boolean active;
    private Instant lastLoginAt;
    private Instant createdAt;

    public static StoreUserDto fromEntity(StoreUser user) {
        return StoreUserDto.builder()
                .id(user.getId())
                .portalUserId(user.getPortalUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .cpf(user.getCpf())
                .role(user.getRole())
                .authSource(user.getAuthSource())
                .isSocioActive(user.isSocioActive())
                .socioPlanName(user.getSocioPlanName())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
