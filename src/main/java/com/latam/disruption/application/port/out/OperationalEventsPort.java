package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.OperationalEventsSnapshot;

import java.util.List;

public interface OperationalEventsPort {

    OperationalEventsSnapshot getOperationalEvents(String bookingId, List<String> segmentIds);
}
