package com.latam.disruption.infrastructure.adapter.out.persistence;

import com.latam.disruption.application.port.out.DisruptionPersistencePort;
import com.latam.disruption.domain.exception.BusinessException;
import com.latam.disruption.domain.exception.TipoErrorNegocio;
import com.latam.disruption.domain.model.AuditLogRecord;
import com.latam.disruption.domain.model.HumanCaseRecord;
import com.latam.disruption.domain.model.RebookingRecord;
import com.latam.disruption.infrastructure.adapter.out.persistence.entity.DisruptionAuditLogJpaEntity;
import com.latam.disruption.infrastructure.adapter.out.persistence.entity.HumanEscalationCaseJpaEntity;
import com.latam.disruption.infrastructure.adapter.out.persistence.entity.RebookingRequestJpaEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PostgresDisruptionPersistenceAdapter implements DisruptionPersistencePort {

    private final SpringDataRebookingRepository rebookingRepository;
    private final SpringDataHumanCaseRepository humanCaseRepository;
    private final SpringDataAuditLogRepository auditLogRepository;

    public PostgresDisruptionPersistenceAdapter(
            SpringDataRebookingRepository rebookingRepository,
            SpringDataHumanCaseRepository humanCaseRepository,
            SpringDataAuditLogRepository auditLogRepository
    ) {
        this.rebookingRepository = rebookingRepository;
        this.humanCaseRepository = humanCaseRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public Optional<RebookingRecord> findRebookingByBookingAndKey(String bookingId, String idempotencyKey) {
        return rebookingRepository.findByBookingIdAndIdempotencyKey(bookingId, idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public List<RebookingRecord> findRebookingsByBookingId(String bookingId) {
        return rebookingRepository.findByBookingId(bookingId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public RebookingRecord saveRebooking(RebookingRecord record) {
        try {
            RebookingRequestJpaEntity entity = new RebookingRequestJpaEntity(
                    record.id(),
                    record.bookingId(),
                    record.idempotencyKey(),
                    record.optionId(),
                    record.flightNumber(),
                    record.policyRef(),
                    record.upstreamRequestId(),
                    record.status(),
                    record.notificationId(),
                    record.expiresAt(),
                    record.createdAt()
            );
            RebookingRequestJpaEntity saved = rebookingRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("uk_rebooking_one_active_per_booking")) {
                throw new BusinessException(
                        TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                        "BOOKING_REBOOKING_ALREADY_ACTIVE_OR_INDETERMINATE",
                        "Ya existe una reacomodacion activa o en curso para esta reserva"
                );
            }
            throw new BusinessException(
                    TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                    "CONCURRENT_IDEMPOTENCY_CONFLICT",
                    "Conflicto de concurrencia sobre la restriccion unica de idempotencia en reacomodacion"
            );
        }
    }

    @Override
    public Optional<HumanCaseRecord> findHumanCaseByBookingAndKey(String bookingId, String idempotencyKey) {
        return humanCaseRepository.findByBookingIdAndIdempotencyKey(bookingId, idempotencyKey)
                .map(this::toDomain);
    }

    @Override
    public HumanCaseRecord saveHumanCase(HumanCaseRecord record) {
        try {
            HumanEscalationCaseJpaEntity entity = new HumanEscalationCaseJpaEntity(
                    record.id(),
                    record.bookingId(),
                    record.idempotencyKey(),
                    record.caseId(),
                    record.queueName(),
                    record.slaMinutes(),
                    record.reason(),
                    record.sanitizedEvidence(),
                    record.createdAt()
            );
            HumanEscalationCaseJpaEntity saved = humanCaseRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                    TipoErrorNegocio.CONFLICTO_IDEMPOTENCIA,
                    "CONCURRENT_IDEMPOTENCY_CONFLICT",
                    "Conflicto de concurrencia sobre la restriccion unica de idempotencia en caso humano"
            );
        }
    }

    @Override
    public AuditLogRecord saveAuditLog(AuditLogRecord record) {
        DisruptionAuditLogJpaEntity entity = new DisruptionAuditLogJpaEntity(
                record.id(),
                record.bookingId(),
                record.operationType(),
                record.resolutionMode(),
                record.policyRef(),
                record.aiConfidence(),
                record.humanApprovalRequired(),
                record.summary(),
                record.createdAt()
        );
        DisruptionAuditLogJpaEntity saved = auditLogRepository.saveAndFlush(entity);
        return new AuditLogRecord(
                saved.getId(),
                saved.getBookingId(),
                saved.getOperationType(),
                saved.getResolutionMode(),
                saved.getPolicyRef(),
                saved.getAiConfidence(),
                saved.isHumanApprovalRequired(),
                saved.getSummary(),
                saved.getCreatedAt()
        );
    }

    private RebookingRecord toDomain(RebookingRequestJpaEntity entity) {
        return new RebookingRecord(
                entity.getId(),
                entity.getBookingId(),
                entity.getIdempotencyKey(),
                entity.getOptionId(),
                entity.getFlightNumber(),
                entity.getPolicyRef(),
                entity.getUpstreamRequestId(),
                entity.getStatus(),
                entity.getNotificationId(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }

    private HumanCaseRecord toDomain(HumanEscalationCaseJpaEntity entity) {
        return new HumanCaseRecord(
                entity.getId(),
                entity.getBookingId(),
                entity.getIdempotencyKey(),
                entity.getCaseId(),
                entity.getQueueName(),
                entity.getSlaMinutes(),
                entity.getReason(),
                entity.getSanitizedEvidence(),
                entity.getCreatedAt()
        );
    }
}
