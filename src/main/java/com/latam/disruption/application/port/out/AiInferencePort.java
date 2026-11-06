package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.AiDraftResponse;
import com.latam.disruption.domain.model.AnonymizedAiPromptContext;

public interface AiInferencePort {

    AiDraftResponse inferInquiryDraft(AnonymizedAiPromptContext promptContext);
}
