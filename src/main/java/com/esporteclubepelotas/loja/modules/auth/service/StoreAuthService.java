package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.common.exception.UnauthorizedException;
import com.esporteclubepelotas.loja.common.security.JwtTokenProvider;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.auth.dto.*;
import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreAuthService {

    private static final Set<String> ALLOWED_PDV_ROLES = Set.of(
            "OWNER", "ADMIN", "STORE_MANAGER", "SELLER", "STOCK_CLERK"
    );

    private final PortalIntegrationClient portalIntegrationClient;
    private final StoreUserRepository storeUserRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        PortalUserSyncDto portalUser = portalIntegrationClient.authenticateWithPortal(request);
        StoreUser storeUser = syncPortalUser(portalUser);

        if (!storeUser.isActive()) {
            throw new UnauthorizedException("Usuário inativo na Loja Oficial.");
        }

        UserPrincipal principal = toPrincipal(storeUser);
        return AuthResponseDto.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(principal))
                .refreshToken(jwtTokenProvider.generateRefreshToken(principal))
                .user(StoreUserDto.fromEntity(storeUser))
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponseDto refreshToken(String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new UnauthorizedException("Refresh token inválido ou expirado.");
        }
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw new UnauthorizedException("Token inválido para renovação.");
        }

        StoreUser user = storeUserRepository.findById(claims.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado."));
        Integer tv = claims.get("tv", Integer.class);
        if (!user.isActive() || (tv != null && !tv.equals(user.getTokenVersion()))) {
            throw new UnauthorizedException("Sessão expirada.");
        }

        UserPrincipal principal = toPrincipal(user);
        return AuthResponseDto.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(principal))
                .refreshToken(jwtTokenProvider.generateRefreshToken(principal))
                .user(StoreUserDto.fromEntity(user))
                .build();
    }

    @Transactional(readOnly = true)
    public StoreUserDto getMe(String userId) {
        StoreUser user = storeUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado."));
        return StoreUserDto.fromEntity(user);
    }

    public static boolean isPdvStaffRole(String role) {
        return role != null && ALLOWED_PDV_ROLES.contains(role.toUpperCase());
    }

    private StoreUser syncPortalUser(PortalUserSyncDto portalUser) {
        StoreUser user = storeUserRepository.findByPortalUserId(portalUser.getPortalUserId())
                .orElseGet(() -> StoreUser.builder()
                        .portalUserId(portalUser.getPortalUserId())
                        .active(true)
                        .tokenVersion(1)
                        .build());

        String normalizedRole = portalUser.getRole() != null && !portalUser.getRole().isBlank()
                ? portalUser.getRole().trim().toUpperCase()
                : "USER";

        String fullName = portalUser.getFullName() != null && !portalUser.getFullName().isBlank()
                ? portalUser.getFullName().trim()
                : ((portalUser.getFirstName() != null ? portalUser.getFirstName() : "") + " "
                        + (portalUser.getLastName() != null ? portalUser.getLastName() : "")).trim();

        user.setFullName(fullName.isEmpty() ? "Usuário Portal" : fullName);
        user.setEmail(portalUser.getEmail());
        user.setCpf(portalUser.getCpf());
        user.setRole(normalizedRole);
        user.setSocioActive(portalUser.isSocioActive() || "ACTIVE".equalsIgnoreCase(portalUser.getSocioStatus()));
        user.setSocioPlanName(portalUser.getSocioPlanName());
        user.setLastLoginAt(Instant.now());

        return storeUserRepository.save(user);
    }

    private UserPrincipal toPrincipal(StoreUser user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .portalUserId(user.getPortalUserId())
                .name(user.getFullName())
                .email(user.getEmail())
                .cpf(user.getCpf())
                .role(user.getRole())
                .isSocioActive(user.isSocioActive())
                .tokenVersion(user.getTokenVersion())
                .active(user.isActive())
                .build();
    }
}
