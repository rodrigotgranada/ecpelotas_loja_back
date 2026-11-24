package com.esporteclubepelotas.loja.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.secret:ecpelotas-loja-alcateia-dev-jwt-secret-1908-pelotas-local}")
    private String jwtSecret;

    @Value("${security.jwt.expires-in:1h}")
    private String jwtExpiresIn;

    private SecretKey key;
    private long jwtExpirationInMs;

    @PostConstruct
    public void init() {
        String paddedSecret = jwtSecret;
        while (paddedSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            paddedSecret += "-ecpelotas-loja-1908";
        }
        this.key = Keys.hmacShaKeyFor(paddedSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationInMs = parseDurationToMillis(jwtExpiresIn);
    }

    private long parseDurationToMillis(String duration) {
        if (duration == null || duration.isBlank()) return 3600000L;
        String trimmed = duration.trim().toLowerCase();
        try {
            if (trimmed.endsWith("h")) return Long.parseLong(trimmed.replace("h", "")) * 3600000L;
            if (trimmed.endsWith("d")) return Long.parseLong(trimmed.replace("d", "")) * 86400000L;
            if (trimmed.endsWith("m")) return Long.parseLong(trimmed.replace("m", "")) * 60000L;
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return 3600000L;
        }
    }

    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        int tv = principal.getTokenVersion() != null ? principal.getTokenVersion() : 1;

        return Jwts.builder()
                .subject(principal.getId())
                .claim("type", "ACCESS")
                .claim("tv", tv)
                .claim("portalUserId", principal.getPortalUserId())
                .claim("email", principal.getEmail())
                .claim("name", principal.getName())
                .claim("cpf", principal.getCpf())
                .claim("role", principal.getRole())
                .claim("isSocioActive", principal.isSocioActive())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 7L * 24 * 3600000L);
        int tv = principal.getTokenVersion() != null ? principal.getTokenVersion() : 1;

        return Jwts.builder()
                .subject(principal.getId())
                .claim("type", "REFRESH")
                .claim("tv", tv)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
