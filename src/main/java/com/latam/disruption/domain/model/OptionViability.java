package com.latam.disruption.domain.model;

import java.util.List;

public record OptionViability(
        CandidateOption option,
        boolean eligible,
        List<String> rejectionReasons
) {
}
