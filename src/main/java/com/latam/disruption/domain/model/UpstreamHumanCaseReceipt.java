package com.latam.disruption.domain.model;

public record UpstreamHumanCaseReceipt(
        String caseId,
        String queue,
        int slaMinutes
) {
}
