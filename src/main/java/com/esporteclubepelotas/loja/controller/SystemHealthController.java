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
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "Loja Oficial Alcateia - API Java (Spring Boot 3)");
        response.put("club", "Esporte Clube Pelotas");
        response.put("stage", resolveIsProdStage() ? "PROD" : "DEV");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("healthy", true);
        response.put("stage", resolveIsProdStage() ? "PROD" : "DEV");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/env-test")
    public ResponseEntity<Map<String, Object>> envTest() {
        boolean isProd = resolveIsProdStage();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("stage", isProd ? "PROD" : "DEV");
        response.put("environment", isProd ? "production" : "development");
        response.put("storageProvider", "firebase");
        return ResponseEntity.ok(response);
    }

    private boolean resolveIsProdStage() {
        String explicitStage = firstNonBlank(
                environment.getProperty("app.stage"),
                environment.getProperty("APP_STAGE"),
                environment.getProperty("app.env"),
                environment.getProperty("APP_ENV"));
        if (explicitStage != null) {
            String normalized = explicitStage.trim().toLowerCase(Locale.ROOT);
            if ("prod".equals(normalized) || "production".equals(normalized)) {
                return true;
            }
            if ("dev".equals(normalized) || "development".equals(normalized)
                    || "staging".equals(normalized) || "homolog".equals(normalized)
                    || "preview".equals(normalized) || "local".equals(normalized)
                    || "test".equals(normalized)) {
                return false;
            }
        }

        String gitBranch = firstNonBlank(
                environment.getProperty("RENDER_GIT_BRANCH"),
                environment.getProperty("GIT_BRANCH"));
        if (gitBranch != null) {
            String branchLower = gitBranch.trim().toLowerCase(Locale.ROOT);
            if (!"main".equals(branchLower) && !"master".equals(branchLower)) {
                return false;
            }
        }

        String renderService = firstNonBlank(
                environment.getProperty("RENDER_SERVICE_NAME"),
                environment.getProperty("RENDER_EXTERNAL_HOSTNAME"));
        if (containsAnyMarker(renderService, "-dev", "dev-", "develop", "homolog", "staging", "preview")) {
            return false;
        }

        boolean hasProdProfile = environment.acceptsProfiles(Profiles.of("prod"))
                || (activeProfile != null && activeProfile.toLowerCase(Locale.ROOT).contains("prod"));
        if (!hasProdProfile) {
            return false;
        }

        String firebaseProject = firstNonBlank(
                environment.getProperty("firebase.project-id"),
                environment.getProperty("firebase.storage-bucket"));
        if (containsAnyMarker(firebaseProject, "dev", "homolog", "staging", "local", "test")) {
            return false;
        }

        String frontendUrl = environment.getProperty("frontend.url");
        if (containsAnyMarker(frontendUrl,
                "localhost", "127.0.0.1", "dev.", "-dev", "_dev", "-git-develop", "homolog", "staging")) {
            return false;
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url");
        return !containsAnyMarker(datasourceUrl,
                "localhost", "127.0.0.1", "ecpelotas_loja_local", "loja_dev", "_dev", "-dev", "homolog", "staging");
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
