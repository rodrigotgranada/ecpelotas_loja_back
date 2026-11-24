package com.esporteclubepelotas.loja.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long ONE_MINUTE_MS = 60_000L;

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!rateLimitEnabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";

        boolean allowed = true;
        if ("POST".equalsIgnoreCase(method)) {
            if (path.endsWith("/auth/login")) {
                allowed = rateLimiterService.tryAcquire("loja-login:" + clientIp, 10, ONE_MINUTE_MS);
            } else if (path.endsWith("/auth/refresh")) {
                allowed = rateLimiterService.tryAcquire("loja-refresh:" + clientIp, 20, ONE_MINUTE_MS);
            }
        }

        if (!allowed) {
            log.warn("Rate limit excedido na Loja para IP {} em {} {}", clientIp, method, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "title", "Muitas tentativas",
                    "detail", "Limite temporário de requisições excedido. Aguarde 1 minuto e tente novamente."
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
