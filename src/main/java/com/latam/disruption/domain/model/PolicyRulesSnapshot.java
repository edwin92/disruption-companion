package com.latam.disruption.domain.model;

import java.time.Instant;
import java.util.List;

public record PolicyRulesSnapshot(
        String scenario,
        String version,
        String effectiveFrom,
        List<PolicyRule> rules,
        Instant retrievedAt,
        List<String> supersededVersions
) {
}
