package com.esporteclubepelotas.loja.modules.orders.dto;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettingsDto {

    @NotBlank(message = "Nome da loja é obrigatório")
    private String storeName;

    @NotBlank(message = "Número de WhatsApp é obrigatório")
    private String whatsappNumber;

    @NotBlank(message = "Horário de abertura é obrigatório")
    private String openHour;

    @NotBlank(message = "Horário de fechamento é obrigatório")
    private String closeHour;

    @NotBlank(message = "Dias de funcionamento são obrigatórios")
    private String workingDays;

    @Min(30)
    @Max(1440)
    private int reservationTtlMinutes;

    @Min(1)
    @Max(10)
    private int maxOpenReservationsPerUser;

    public static StoreSettingsDto fromEntity(StoreSettings s) {
        return StoreSettingsDto.builder()
                .storeName(s.getStoreName())
                .whatsappNumber(s.getWhatsappNumber())
                .openHour(s.getOpenHour())
                .closeHour(s.getCloseHour())
                .workingDays(s.getWorkingDays())
                .reservationTtlMinutes(s.getReservationTtlMinutes())
                .maxOpenReservationsPerUser(s.getMaxOpenReservationsPerUser())
                .build();
    }
}
