package com.esporteclubepelotas.loja.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public class SystemHealthController {

    private final Environment environment;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public SystemHealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        String stage = resolveStage();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "Loja Oficial Alcateia - API Java (Spring Boot 3)");
        response.put("club", "Esporte Clube Pelotas");
        response.put("stage", stage);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String stage = resolveStage();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("healthy", true);
        response.put("stage", stage);
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/env-test")
    public ResponseEntity<Map<String, Object>> envTest() {
        String stage = resolveStage();
        String environmentLabel = switch (stage) {
            case "PROD" -> "production";
            case "DEV" -> "development";
            default -> "local";
        };

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stage", stage);
        response.put("environment", environmentLabel);
        response.put("databaseLabel", "LOCAL".equals(stage) ? "PostgreSQL Local (5434)" : "Neon PostgreSQL (" + stage + ")");
        response.put("storageProvider", "firebase");
        return ResponseEntity.ok(response);
    }

    private String resolveStage() {
        String explicitStage = firstNonBlank(
                environment.getProperty("app.stage"),
                environment.getProperty("APP_STAGE"),
                environment.getProperty("app.env"),
                environment.getProperty("APP_ENV"));
        if (explicitStage != null) {
            String normalized = explicitStage.trim().toLowerCase(Locale.ROOT);
            if ("local".equals(normalized)) {
                return "LOCAL";
            }
            if ("prod".equals(normalized) || "production".equals(normalized)) {
                return "PROD";
            }
            if ("dev".equals(normalized) || "development".equals(normalized)
                    || "staging".equals(normalized) || "homolog".equals(normalized)
                    || "preview".equals(normalized) || "test".equals(normalized)) {
                return "DEV";
            }
        }

        String gitBranch = firstNonBlank(
                environment.getProperty("RENDER_GIT_BRANCH"),
                environment.getProperty("GIT_BRANCH"));
        if (gitBranch != null) {
            String branchLower = gitBranch.trim().toLowerCase(Locale.ROOT);
            return ("main".equals(branchLower) || "master".equals(branchLower)) ? "PROD" : "DEV";
        }

        String renderService = firstNonBlank(
                environment.getProperty("RENDER_SERVICE_NAME"),
                environment.getProperty("RENDER_EXTERNAL_HOSTNAME"));
        if (renderService != null) {
            return containsAnyMarker(renderService, "-dev", "dev-", "develop", "homolog", "staging", "preview")
                    ? "DEV"
                    : "PROD";
        }

        boolean hasProdProfile = environment.acceptsProfiles(Profiles.of("prod"))
                || (activeProfile != null && activeProfile.toLowerCase(Locale.ROOT).contains("prod"));

        String datasourceUrl = environment.getProperty("spring.datasource.url");
        if (!hasProdProfile || containsAnyMarker(datasourceUrl,
                "localhost", "127.0.0.1", "jdbc:h2", "jdbc:postgresql://postgres:", "ecpelotas_loja_local")) {
            return "LOCAL";
        }

        String frontendUrl = environment.getProperty("frontend.url");
        if (containsAnyMarker(frontendUrl,
                "dev.", "-dev", "_dev", "-git-develop", "homolog", "staging")) {
            return "DEV";
        }

        if (containsAnyMarker(datasourceUrl, "loja_dev", "_dev", "-dev", "homolog", "staging")) {
            return "DEV";
        }

        return "PROD";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean containsAnyMarker(String value, String... markers) {
        if (value == null || value.isBlank()) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
