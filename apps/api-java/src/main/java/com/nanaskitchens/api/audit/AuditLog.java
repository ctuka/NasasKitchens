package com.nanaskitchens.api.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Maps onto the Prisma-managed "AuditLog" table ("before"/"after" jsonb columns left unmapped). */
@Entity
@Table(name = "AuditLog")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String entity;

    @Column(nullable = false)
    private String action;

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    // Kept explicitly in addition to Lombok so this entity compiles in minimal Maven
    // environments where annotation processing is disabled.
    public void setActor(String actor) {
        this.actor = actor;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (at == null) {
            at = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
