package com.latam.disruption.application.port.in;

import com.latam.disruption.application.dto.RebookingCommand;
import com.latam.disruption.application.dto.RebookingResult;

public interface CreateRebookingUseCase {

    RebookingResult createRebooking(RebookingCommand command);
}
