package com.esporteclubepelotas.loja.modules.auth.controller;

import com.esporteclubepelotas.loja.common.security.CurrentUser;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.auth.dto.AuthResponseDto;
import com.esporteclubepelotas.loja.modules.auth.dto.LoginRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.StoreUserDto;
import com.esporteclubepelotas.loja.modules.auth.service.StoreAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Store Auth", description = "Autenticação federada via Portal E.C. Pelotas")
public class StoreAuthController {

    private final StoreAuthService storeAuthService;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/login")
    @Operation(summary = "Autentica na Loja validando credenciais locais ou S2S no Portal")
    public ResponseEntity<AuthResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response
    ) {
        AuthResponseDto result = storeAuthService.login(request);
        setRefreshCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sso")
    @Operation(summary = "Troca um token SSO descartável do Portal por sessão JWT na Loja")
    public ResponseEntity<AuthResponseDto> loginWithSso(
            @RequestBody Map<String, String> payload,
            HttpServletResponse response
    ) {
        String ssoToken = payload != null ? payload.get("token") : null;
        AuthResponseDto result = storeAuthService.loginWithSso(ssoToken);
        setRefreshCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o Access Token da Loja via Refresh Token HttpOnly")
    public ResponseEntity<AuthResponseDto> refresh(
            @RequestBody(required = false) Map<String, String> payload,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = payload != null && payload.get("refreshToken") != null
                ? payload.get("refreshToken")
                : extractCookie(request, "loja_refresh_token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        AuthResponseDto result = storeAuthService.refreshToken(token);
        setRefreshCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra sessão da Loja")
    public ResponseEntity<Map<String, Boolean>> logout(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("loja_refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(cookieSameSite != null && !cookieSameSite.isBlank() ? cookieSameSite.trim() : "Lax");
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/me")
    @Operation(summary = "Retorna usuário logado na Loja")
    public ResponseEntity<StoreUserDto> me(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(storeAuthService.getMe(principal.getId()));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        if (token == null) return;
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("loja_refresh_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite(cookieSameSite != null && !cookieSameSite.isBlank() ? cookieSameSite.trim() : "Lax");
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
