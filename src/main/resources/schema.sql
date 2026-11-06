CREATE TABLE IF NOT EXISTS rebooking_requests (
    id UUID PRIMARY KEY,
    booking_id VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    option_id VARCHAR(16) NOT NULL,
    flight_number VARCHAR(16) NOT NULL,
    policy_ref VARCHAR(32) NOT NULL,
    upstream_request_id VARCHAR(80),
    status VARCHAR(48) NOT NULL,
    notification_id VARCHAR(32),
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_rebooking_booking_idempotency UNIQUE (booking_id, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rebooking_one_active_per_booking
ON rebooking_requests (booking_id)
WHERE status IN ('IN_PROGRESS', 'PENDING_CONFIRMATION', 'CONFIRMED', 'INDETERMINATE_PENDING_RECONCILIATION');

CREATE TABLE IF NOT EXISTS human_cases (
    id UUID PRIMARY KEY,
    booking_id VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    case_id VARCHAR(32) NOT NULL,
    queue_name VARCHAR(32) NOT NULL,
    sla_minutes INTEGER NOT NULL,
    reason VARCHAR(320) NOT NULL,
    sanitized_evidence TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_humancase_booking_idempotency UNIQUE (booking_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS disruption_audit_logs (
    id UUID PRIMARY KEY,
    booking_id VARCHAR(16) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    resolution_mode VARCHAR(64) NOT NULL,
    policy_ref VARCHAR(32) NOT NULL,
    ai_confidence DOUBLE PRECISION,
    human_approval_required BOOLEAN NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
