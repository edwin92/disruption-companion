package com.latam.disruption.application.port.in;

import com.latam.disruption.application.dto.EscalationCommand;
import com.latam.disruption.application.dto.EscalationResult;

public interface CreateHumanEscalationUseCase {

    EscalationResult createHumanEscalation(EscalationCommand command);
}
