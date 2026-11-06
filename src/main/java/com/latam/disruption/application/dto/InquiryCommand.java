package com.latam.disruption.application.dto;

public record InquiryCommand(
        String bookingId,
        String question
) {
}
