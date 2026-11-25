package com.esporteclubepelotas.loja.common.security;

import com.esporteclubepelotas.loja.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class StoreInternalKeyValidator {

    @Value("${app.portal.internal-api-key:}")
    private String portalInternalApiKey;

    public void validateOrThrow(String providedKey) {
        if (portalInternalApiKey == null || portalInternalApiKey.isBlank()
                || providedKey == null || providedKey.isBlank()) {
            throw new UnauthorizedException("Chave de integração interna inválida ou não configurada.");
        }
        boolean match = MessageDigest.isEqual(
                portalInternalApiKey.trim().getBytes(StandardCharsets.UTF_8),
                providedKey.trim().getBytes(StandardCharsets.UTF_8)
        );
        if (!match) {
            throw new UnauthorizedException("Chave de integração interna inválida.");
        }
    }
}
