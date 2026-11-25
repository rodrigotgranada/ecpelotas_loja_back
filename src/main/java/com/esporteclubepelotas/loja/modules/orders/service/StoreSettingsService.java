package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.orders.dto.StorePublicConfigDto;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private static final String DEFAULT_ID = "default";

    private final StoreSettingsRepository storeSettingsRepository;

    @Transactional
    public StoreSettings getOrCreateDefault() {
        return storeSettingsRepository.findById(DEFAULT_ID)
                .orElseGet(() -> storeSettingsRepository.save(StoreSettings.builder().id(DEFAULT_ID).build()));
    }

    @Transactional
    public StorePublicConfigDto getPublicConfig() {
        return StorePublicConfigDto.fromEntity(getOrCreateDefault());
    }
}
