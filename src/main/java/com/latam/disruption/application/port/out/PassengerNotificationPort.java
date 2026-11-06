package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.PassengerNotificationReceipt;

public interface PassengerNotificationPort {

    PassengerNotificationReceipt notifyPassenger(
            String bookingId,
            String message,
            String validatedPolicyRef
    );
}
