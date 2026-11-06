package com.latam.disruption.infrastructure.adapter.out.persistence;

import com.latam.disruption.infrastructure.adapter.out.persistence.entity.HumanEscalationCaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataHumanCaseRepository extends JpaRepository<HumanEscalationCaseJpaEntity, UUID> {

    Optional<HumanEscalationCaseJpaEntity> findByBookingIdAndIdempotencyKey(
            String bookingId,
            String idempotencyKey
    );
}
