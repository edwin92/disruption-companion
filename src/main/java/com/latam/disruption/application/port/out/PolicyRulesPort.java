package com.latam.disruption.application.port.out;

import com.latam.disruption.domain.model.PolicyRulesSnapshot;

public interface PolicyRulesPort {

    PolicyRulesSnapshot getPolicyRules(String scenario);
}
