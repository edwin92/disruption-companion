package com.latam.disruption.application.port.in;

import com.latam.disruption.application.dto.InquiryCommand;
import com.latam.disruption.application.dto.InquiryResult;

public interface ResolvePassengerInquiryUseCase {

    InquiryResult resolveInquiry(InquiryCommand command);
}
