package com.esporteclubepelotas.loja.modules.orders.repository;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, String> {
}
