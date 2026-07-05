package com.nanaskitchens.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps onto the Prisma-managed "RefreshToken" table in the shared culture_eats database. */
@Entity
@Table(name = "RefreshToken")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    private String id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "tokenHash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revokedAt")
    private LocalDateTime revokedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
