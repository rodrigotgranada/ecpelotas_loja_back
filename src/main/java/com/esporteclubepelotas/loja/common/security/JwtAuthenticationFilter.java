package com.esporteclubepelotas.loja.common.security;

import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StoreUserRepository storeUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Claims claims = jwtTokenProvider.getClaimsFromToken(jwt);
                if (!"ACCESS".equals(claims.get("type", String.class))) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = claims.getSubject();
                Integer tokenTv = claims.get("tv", Integer.class);
                StoreUser user = storeUserRepository.findById(userId).orElse(null);
                if (user != null && user.isActive()
                        && (tokenTv == null || tokenTv.equals(user.getTokenVersion()))) {
                    UserPrincipal principal = UserPrincipal.builder()
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

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception ex) {
            logger.error("Erro ao validar JWT na Loja: " + ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
