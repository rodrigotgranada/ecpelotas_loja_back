package com.esporteclubepelotas.loja.modules.orders.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
