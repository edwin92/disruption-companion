package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.EligibleOptionsSnapshot;
import com.latam.disruption.domain.model.TripContext;

public interface EligibleOptionsPort {

    EligibleOptionsSnapshot getEligibleOptions(TripContext tripContext);
}
