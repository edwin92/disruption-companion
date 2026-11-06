package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.UpstreamHumanCaseReceipt;

public interface HumanCaseGatewayPort {

    UpstreamHumanCaseReceipt createHumanCase(
            String bookingId,
            String reason,
            String sanitizedEvidence
    );
}
