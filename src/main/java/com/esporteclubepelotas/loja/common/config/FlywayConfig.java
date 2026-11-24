package com.esporteclubepelotas.loja.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(Environment environment) {
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod"));
        return flyway -> {
            if (!isProd) {
                log.info("Ambiente nao-prod ativo: executando Flyway repair() antes de migrate().");
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
