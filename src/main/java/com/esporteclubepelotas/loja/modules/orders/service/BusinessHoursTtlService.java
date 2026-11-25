package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreSettings;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BusinessHoursTtlService {

    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    public Instant calculateExpiresAt(Instant startUtc, StoreSettings settings) {
        int remainingMinutes = Math.max(30, settings.getReservationTtlMinutes());
        LocalTime openTime = parseTime(settings.getOpenHour(), LocalTime.of(9, 0));
        LocalTime closeTime = parseTime(settings.getCloseHour(), LocalTime.of(19, 0));
        if (!closeTime.isAfter(openTime)) {
            closeTime = LocalTime.of(19, 0);
        }

        Set<DayOfWeek> workDays = parseWorkingDays(settings.getWorkingDays());
        ZonedDateTime cursor = startUtc.atZone(STORE_ZONE);

        int guardDays = 0;
        while (remainingMinutes > 0 && guardDays < 30) {
            if (!workDays.contains(cursor.getDayOfWeek())) {
                cursor = cursor.plusDays(1).with(openTime).withSecond(0).withNano(0);
                guardDays++;
                continue;
            }

            LocalTime currentTime = cursor.toLocalTime();
            if (currentTime.isBefore(openTime)) {
                cursor = cursor.with(openTime).withSecond(0).withNano(0);
            } else if (!currentTime.isBefore(closeTime)) {
                cursor = cursor.plusDays(1).with(openTime).withSecond(0).withNano(0);
                guardDays++;
                continue;
            }

            long minutesUntilClose = Duration.between(cursor.toLocalTime(), closeTime).toMinutes();
            if (minutesUntilClose >= remainingMinutes) {
                cursor = cursor.plusMinutes(remainingMinutes);
                remainingMinutes = 0;
            } else {
                remainingMinutes -= (int) minutesUntilClose;
                cursor = cursor.plusDays(1).with(openTime).withSecond(0).withNano(0);
                guardDays++;
            }
        }

        return cursor.toInstant();
    }

    private LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Set<DayOfWeek> parseWorkingDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(this::toDayOfWeek)
                .collect(Collectors.toSet());
    }

    private DayOfWeek toDayOfWeek(String code) {
        return switch (code) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.MONDAY;
        };
    }
}
