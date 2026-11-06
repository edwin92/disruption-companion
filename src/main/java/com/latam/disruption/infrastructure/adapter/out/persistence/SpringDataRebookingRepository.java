package com.latam.disruption.infrastructure.adapter.out.persistence;

import com.latam.disruption.infrastructure.adapter.out.persistence.entity.RebookingRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRebookingRepository extends JpaRepository<RebookingRequestJpaEntity, UUID> {

    Optional<RebookingRequestJpaEntity> findByBookingIdAndIdempotencyKey(
            String bookingId,
            String idempotencyKey
    );

    List<RebookingRequestJpaEntity> findByBookingId(String bookingId);
}
