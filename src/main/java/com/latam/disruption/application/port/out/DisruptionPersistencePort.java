package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.AuditLogRecord;
import com.latam.disruption.domain.model.HumanCaseRecord;
import com.latam.disruption.domain.model.RebookingRecord;

import java.util.List;
import java.util.Optional;

public interface DisruptionPersistencePort {

    Optional<RebookingRecord> findRebookingByBookingAndKey(String bookingId, String idempotencyKey);

    List<RebookingRecord> findRebookingsByBookingId(String bookingId);

    RebookingRecord saveRebooking(RebookingRecord record);

    Optional<HumanCaseRecord> findHumanCaseByBookingAndKey(String bookingId, String idempotencyKey);

    HumanCaseRecord saveHumanCase(HumanCaseRecord record);

    AuditLogRecord saveAuditLog(AuditLogRecord record);
}
