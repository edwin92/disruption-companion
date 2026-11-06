package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.CandidateOption;
import com.latam.disruption.domain.model.IdempotencyKey;
import com.latam.disruption.domain.model.UpstreamRebookingReceipt;

public interface RebookingGatewayPort {

    UpstreamRebookingReceipt createRebookingRequest(
            String bookingId,
            CandidateOption option,
            IdempotencyKey idempotencyKey
    );
}
