package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.modules.auth.dto.LoginRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.PortalUserSyncDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortalReadonlyFallbackService {

    private final PasswordEncoder passwordEncoder;

    @Value("${app.portal.readonly-db.url:}")
    private String readonlyDbUrl;

    @Value("${app.portal.readonly-db.username:}")
    private String readonlyDbUsername;

    @Value("${app.portal.readonly-db.password:}")
    private String readonlyDbPassword;

    public boolean isConfigured() {
        return readonlyDbUrl != null && !readonlyDbUrl.isBlank();
    }

    public Optional<PortalUserSyncDto> authenticateReadonlyFallback(LoginRequestDto request) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String rawIdentifier = request.getCpf() != null ? request.getCpf().trim() : "";
        String cleanCpf = rawIdentifier.replaceAll("[^0-9]", "");
        boolean isEmail = rawIdentifier.contains("@");

        String sql = isEmail
                ? """
                  SELECT u.id, u.first_name, u.last_name, u.email, u.cpf, u.password_hash,
                         u.is_socio, u.socio_status, u.socio_plan_name, u.is_active, u.status,
                         r.name AS role_name
                  FROM users u
                  LEFT JOIN roles r ON r.id = u.role_id
                  WHERE LOWER(u.email) = LOWER(?)
                  LIMIT 1
                  """
                : """
                  SELECT u.id, u.first_name, u.last_name, u.email, u.cpf, u.password_hash,
                         u.is_socio, u.socio_status, u.socio_plan_name, u.is_active, u.status,
                         r.name AS role_name
                  FROM users u
                  LEFT JOIN roles r ON r.id = u.role_id
                  WHERE u.cpf = ?
                  LIMIT 1
                  """;

        DriverManager.setLoginTimeout(3);
        try (Connection conn = DriverManager.getConnection(
                readonlyDbUrl.trim(),
                readonlyDbUsername != null ? readonlyDbUsername.trim() : "",
                readonlyDbPassword != null ? readonlyDbPassword : ""
        );
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, isEmail ? rawIdentifier : cleanCpf);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                boolean isActive = rs.getBoolean("is_active");
                String status = rs.getString("status");
                String passwordHash = rs.getString("password_hash");
                if (!isActive || !"ACTIVE".equalsIgnoreCase(status)
                        || passwordHash == null || !passwordEncoder.matches(request.getPassword(), passwordHash)) {
                    return Optional.empty();
                }

                String firstName = rs.getString("first_name");
                String lastName = rs.getString("last_name");
                String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();

                return Optional.of(PortalUserSyncDto.builder()
                        .portalUserId(rs.getString("id"))
                        .firstName(firstName)
                        .lastName(lastName)
                        .fullName(fullName)
                        .email(rs.getString("email"))
                        .cpf(rs.getString("cpf"))
                        .role(rs.getString("role_name"))
                        .socioActive(rs.getBoolean("is_socio") && "ACTIVE".equalsIgnoreCase(rs.getString("socio_status")))
                        .socioStatus(rs.getString("socio_status"))
                        .socioPlanName(rs.getString("socio_plan_name"))
                        .build());
            }
        } catch (SQLException ex) {
            log.warn("Fallback READ-ONLY ao banco do Portal falhou: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
