package com.esporteclubepelotas.loja.modules.orders.dto;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorePublicConfigDto {
    private String storeName;
    private String whatsappNumber;
    private String openHour;
    private String closeHour;
    private String workingDays;
    private int reservationTtlMinutes;

    public static StorePublicConfigDto fromEntity(StoreSettings s) {
        return StorePublicConfigDto.builder()
                .storeName(s.getStoreName())
                .whatsappNumber(s.getWhatsappNumber())
                .openHour(s.getOpenHour())
                .closeHour(s.getCloseHour())
                .workingDays(s.getWorkingDays())
                .reservationTtlMinutes(s.getReservationTtlMinutes())
                .build();
    }
}
