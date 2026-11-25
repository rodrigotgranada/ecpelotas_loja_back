package com.esporteclubepelotas.loja.modules.orders.repository;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, Long> {
    Optional<StoreSettings> findFirstByOrderByIdAsc();
}
