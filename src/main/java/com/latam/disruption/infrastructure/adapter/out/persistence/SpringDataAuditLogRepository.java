package com.latam.disruption.infrastructure.adapter.out.persistence;

import com.latam.disruption.infrastructure.adapter.out.persistence.entity.DisruptionAuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAuditLogRepository extends JpaRepository<DisruptionAuditLogJpaEntity, UUID> {
}
