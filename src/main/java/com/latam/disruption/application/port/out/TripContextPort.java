package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.BookingId;
import com.latam.disruption.domain.model.TripContext;

import java.util.Optional;

public interface TripContextPort {

    Optional<TripContext> getTripContext(BookingId bookingId);
}
