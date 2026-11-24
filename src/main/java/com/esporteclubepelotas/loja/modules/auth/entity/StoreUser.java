package com.esporteclubepelotas.loja.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreUser {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "portal_user_id", nullable = false, unique = true, length = 36)
    private String portalUserId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String cpf;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String role = "USER";

    @Column(name = "is_socio_active", nullable = false)
    @Builder.Default
    private boolean isSocioActive = false;

    @Column(name = "socio_plan_name", length = 100)
    private String socioPlanName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 1;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
