package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.UnauthorizedException;
import com.esporteclubepelotas.loja.common.security.JwtTokenProvider;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.auth.dto.*;
import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreAuthService {

    private static final Set<String> ALLOWED_PDV_ROLES = Set.of(
            "OWNER", "ADMIN", "STORE_MANAGER", "SELLER", "STOCK_CLERK");

    private static final Set<String> STORE_SPECIFIC_ROLES = Set.of(
            "STORE_MANAGER", "SELLER", "STOCK_CLERK");

    private final PortalIntegrationClient portalIntegrationClient;
    private final PortalSsoTokenValidator portalSsoTokenValidator;
    private final PortalReadonlyFallbackService portalReadonlyFallbackService;
    private final StoreUserRepository storeUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        String identifier = request.getCpf() != null ? request.getCpf().trim() : "";
        Optional<StoreUser> localMatch = findLocalUserByIdentifier(identifier);

        // 1. Colaborador exclusivo da Loja (LOCAL) — nunca depende do Portal
        if (localMatch.isPresent()
                && "LOCAL".equalsIgnoreCase(localMatch.get().getAuthSource())
                && localMatch.get().getPasswordHash() != null) {
            return authenticateLocalAccount(localMatch.get(), request.getPassword());
        }

        // 2. Usuário do Portal — tenta S2S primeiro; se Portal API cair, usa cache em
        // store_users ou fallback READ-ONLY
        try {
            PortalUserSyncDto portalUser = portalIntegrationClient.authenticateWithPortal(request);
            StoreUser synced = syncPortalUser(portalUser, request.getPassword());
            ensureUserActive(synced);
            return buildAuthResponse(synced);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (BusinessException portalOfflineEx) {
            return authenticateWithPortalOfflineFallbacks(request, localMatch, portalOfflineEx);
        }
    }

    @Transactional
    public AuthResponseDto loginWithSso(String ssoToken) {
        PortalUserSyncDto portalUser = portalSsoTokenValidator.validateAndExtract(ssoToken);
        StoreUser synced = syncPortalUser(portalUser, null);
        ensureUserActive(synced);
        return buildAuthResponse(synced);
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

        return buildAuthResponse(user);
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

    private AuthResponseDto authenticateLocalAccount(StoreUser localUser, String rawPassword) {
        ensureUserActive(localUser);
        if (!passwordEncoder.matches(rawPassword, localUser.getPasswordHash())) {
            throw new UnauthorizedException("Credenciais inválidas na Loja Oficial.");
        }
        localUser.setLastLoginAt(Instant.now());
        return buildAuthResponse(storeUserRepository.save(localUser));
    }

    private AuthResponseDto authenticateWithPortalOfflineFallbacks(
            LoginRequestDto request,
            Optional<StoreUser> localMatch,
            BusinessException originalError) {
        if (localMatch.isPresent() && localMatch.get().getPortalVerifierHash() != null) {
            StoreUser cached = localMatch.get();
            ensureUserActive(cached);
            if (!passwordEncoder.matches(request.getPassword(), cached.getPortalVerifierHash())) {
                throw new UnauthorizedException("Credenciais inválidas (modo contingência offline).");
            }
            log.info("Autenticado via cache de contingência store_users para usuário {}", cached.getId());
            cached.setLastLoginAt(Instant.now());
            return buildAuthResponse(storeUserRepository.save(cached));
        }

        Optional<PortalUserSyncDto> readonlyUser = portalReadonlyFallbackService.authenticateReadonlyFallback(request);
        if (readonlyUser.isPresent()) {
            log.info("Autenticado via fallback READ-ONLY do banco do Portal para {}",
                    readonlyUser.get().getPortalUserId());
            StoreUser synced = syncPortalUser(readonlyUser.get(), request.getPassword());
            ensureUserActive(synced);
            return buildAuthResponse(synced);
        }

        throw originalError;
    }

    private void ensureUserActive(StoreUser user) {
        if (!user.isActive()) {
            throw new UnauthorizedException("Usuário inativo na Loja Oficial.");
        }
    }

    private Optional<StoreUser> findLocalUserByIdentifier(String identifier) {
        if (identifier.isBlank()) {
            return Optional.empty();
        }
        if (identifier.contains("@")) {
            return storeUserRepository.findByEmailIgnoreCase(identifier);
        }
        String digits = identifier.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        return storeUserRepository.findByCpf(digits);
    }

    private StoreUser syncPortalUser(PortalUserSyncDto portalUser, String rawPasswordForContingencyCache) {
        StoreUser user = storeUserRepository.findByPortalUserId(portalUser.getPortalUserId())
                .orElseGet(() -> StoreUser.builder()
                        .portalUserId(portalUser.getPortalUserId())
                        .authSource("PORTAL")
                        .active(true)
                        .tokenVersion(1)
                        .build());

        String portalRole = portalUser.getRole() != null && !portalUser.getRole().isBlank()
                ? portalUser.getRole().trim().toUpperCase()
                : "USER";

        String existingRole = user.getRole() != null ? user.getRole().trim().toUpperCase() : "USER";
        String resolvedRole = portalRole;
        if (!"OWNER".equals(portalRole) && !"ADMIN".equals(portalRole)
                && STORE_SPECIFIC_ROLES.contains(existingRole)) {
            resolvedRole = existingRole;
        }

        String fullName = portalUser.getFullName() != null && !portalUser.getFullName().isBlank()
                ? portalUser.getFullName().trim()
                : ((portalUser.getFirstName() != null ? portalUser.getFirstName() : "") + " "
                        + (portalUser.getLastName() != null ? portalUser.getLastName() : "")).trim();

        user.setFullName(fullName.isEmpty() ? "Usuário Portal" : fullName);
        user.setEmail(portalUser.getEmail() != null ? portalUser.getEmail().trim().toLowerCase() : null);
        user.setCpf(portalUser.getCpf() != null ? portalUser.getCpf().replaceAll("[^0-9]", "") : null);
        user.setRole(resolvedRole);
        user.setSocioActive(portalUser.isSocioActive() || "ACTIVE".equalsIgnoreCase(portalUser.getSocioStatus()));
        user.setSocioPlanName(portalUser.getSocioPlanName());
        user.setLastLoginAt(Instant.now());

        if (rawPasswordForContingencyCache != null && !rawPasswordForContingencyCache.isBlank()) {
            user.setPortalVerifierHash(passwordEncoder.encode(rawPasswordForContingencyCache));
        }

        return storeUserRepository.save(user);
    }

    private AuthResponseDto buildAuthResponse(StoreUser user) {
        UserPrincipal principal = toPrincipal(user);
        return AuthResponseDto.builder()
                .accessToken(jwtTokenProvider.generateAccessToken(principal))
                .refreshToken(jwtTokenProvider.generateRefreshToken(principal))
                .user(StoreUserDto.fromEntity(user))
                .build();
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
