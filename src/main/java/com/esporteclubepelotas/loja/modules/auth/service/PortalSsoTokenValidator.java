package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.common.exception.UnauthorizedException;
import com.esporteclubepelotas.loja.modules.auth.dto.PortalUserSyncDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PortalSsoTokenValidator {

    @Value("${app.portal.internal-api-key:}")
    private String portalInternalApiKey;

    public PortalUserSyncDto validateAndExtract(String ssoToken) {
        if (portalInternalApiKey == null || portalInternalApiKey.isBlank()) {
            throw new UnauthorizedException("Integração SSO com o Portal não configurada.");
        }
        if (ssoToken == null || ssoToken.isBlank()) {
            throw new UnauthorizedException("Token SSO ausente.");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(deriveSigningKey())
                    .build()
                    .parseSignedClaims(ssoToken.trim())
                    .getPayload();

            if (!"STORE_SSO".equals(claims.get("type", String.class))) {
                throw new UnauthorizedException("Token SSO inválido.");
            }

            String portalUserId = claims.getSubject();
            if (portalUserId == null || portalUserId.isBlank()) {
                throw new UnauthorizedException("Token SSO sem identificador de usuário.");
            }

            return PortalUserSyncDto.builder()
                    .portalUserId(portalUserId)
                    .fullName(claims.get("fullName", String.class))
                    .email(claims.get("email", String.class))
                    .cpf(claims.get("cpf", String.class))
                    .role(claims.get("role", String.class))
                    .socioActive(Boolean.TRUE.equals(claims.get("isSocioActive", Boolean.class)))
                    .socioPlanName(claims.get("socioPlanName", String.class))
                    .socioStatus(claims.get("socioStatus", String.class))
                    .build();
        } catch (JwtException ex) {
            throw new UnauthorizedException("Sessão SSO expirada ou inválida. Entre novamente.");
        }
    }

    private SecretKey deriveSigningKey() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(portalInternalApiKey.trim().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível", e);
        }
    }
}
