package com.esporteclubepelotas.loja.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_name", nullable = false, length = 150)
    @Builder.Default
    private String storeName = "Loja Oficial Alcateia";

    @Column(name = "whatsapp_number", nullable = false, length = 30)
    @Builder.Default
    private String whatsappNumber = "5553999999999";

    @Column(name = "business_open_time", nullable = false, length = 5)
    @Builder.Default
    private String openHour = "09:00";

    @Column(name = "business_close_time", nullable = false, length = 5)
    @Builder.Default
    private String closeHour = "19:00";

    @Column(name = "working_days", nullable = false, length = 60)
    @Builder.Default
    private String workingDays = "MON,TUE,WED,THU,FRI,SAT";

    @Column(name = "reservation_business_hours_ttl_minutes", nullable = false)
    @Builder.Default
    private int reservationTtlMinutes = 180;

    @Column(name = "max_open_reservations_per_phone", nullable = false)
    @Builder.Default
    private int maxOpenReservationsPerUser = 2;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
