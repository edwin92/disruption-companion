package com.latam.disruption.domain.model;

public record PassengerNotificationReceipt(
        String notificationId,
        String channel,
        String status
) {
}
