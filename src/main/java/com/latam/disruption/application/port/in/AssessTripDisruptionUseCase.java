package com.latam.disruption.application.port.in;

import com.latam.disruption.domain.model.DisruptionAssessment;

public interface AssessTripDisruptionUseCase {

    DisruptionAssessment assessTrip(String rawBookingId);
}
