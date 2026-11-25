package com.esporteclubepelotas.loja.modules.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "store_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSettings {

    @Id
    @Column(length = 36)
    @Builder.Default
    private String id = "default";

    @Column(name = "store_name", nullable = false, length = 120)
    @Builder.Default
    private String storeName = "Loja Oficial Alcateia";

    @Column(name = "whatsapp_number", nullable = false, length = 30)
    @Builder.Default
    private String whatsappNumber = "5553999999999";

    @Column(name = "open_hour", nullable = false, length = 5)
    @Builder.Default
    private String openHour = "09:00";

    @Column(name = "close_hour", nullable = false, length = 5)
    @Builder.Default
    private String closeHour = "19:00";

    @Column(name = "working_days", nullable = false, length = 60)
    @Builder.Default
    private String workingDays = "MON,TUE,WED,THU,FRI,SAT";

    @Column(name = "reservation_ttl_minutes", nullable = false)
    @Builder.Default
    private int reservationTtlMinutes = 180;

    @Column(name = "max_open_reservations_per_user", nullable = false)
    @Builder.Default
    private int maxOpenReservationsPerUser = 2;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
