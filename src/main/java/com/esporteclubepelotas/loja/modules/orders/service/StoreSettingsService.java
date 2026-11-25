package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.orders.dto.StorePublicConfigDto;
import com.esporteclubepelotas.loja.modules.orders.dto.StoreSettingsDto;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository storeSettingsRepository;

    @Transactional
    public StoreSettings getOrCreateDefault() {
        return storeSettingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> storeSettingsRepository.save(StoreSettings.builder().build()));
    }

    @Transactional
    public StorePublicConfigDto getPublicConfig() {
        return StorePublicConfigDto.fromEntity(getOrCreateDefault());
    }

    @Transactional
    public StoreSettingsDto getAdminSettings() {
        return StoreSettingsDto.fromEntity(getOrCreateDefault());
    }

    @Transactional
    public StoreSettingsDto updateAdminSettings(StoreSettingsDto dto) {
        StoreSettings settings = getOrCreateDefault();
        settings.setStoreName(dto.getStoreName().trim());
        settings.setWhatsappNumber(dto.getWhatsappNumber().replaceAll("[^0-9]", ""));
        settings.setOpenHour(dto.getOpenHour().trim());
        settings.setCloseHour(dto.getCloseHour().trim());
        settings.setWorkingDays(dto.getWorkingDays().trim().toUpperCase());
        settings.setReservationTtlMinutes(dto.getReservationTtlMinutes());
        settings.setMaxOpenReservationsPerUser(dto.getMaxOpenReservationsPerUser());
        return StoreSettingsDto.fromEntity(storeSettingsRepository.save(settings));
    }
}
