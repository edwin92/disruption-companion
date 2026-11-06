package com.latam.disruption.infrastructure.config;

import com.latam.disruption.application.port.out.AiInferencePort;
import com.latam.disruption.application.port.out.DisruptionPersistencePort;
import com.latam.disruption.application.port.out.EligibleOptionsPort;
import com.latam.disruption.application.port.out.HumanCaseGatewayPort;
import com.latam.disruption.application.port.out.OperationalEventsPort;
import com.latam.disruption.application.port.out.PassengerNotificationPort;
import com.latam.disruption.application.port.out.PolicyRulesPort;
import com.latam.disruption.application.port.out.RebookingGatewayPort;
import com.latam.disruption.application.port.out.TripContextPort;
import com.latam.disruption.application.usecase.DisruptionOrchestratorUseCase;
import com.latam.disruption.domain.service.AiSafetySandwichService;
import com.latam.disruption.domain.service.DisruptionDomainEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@Configuration
public class DomainBeansConfig {

    @Bean
    public Clock evaluationClock(
            @Value("${disruption.clock.fixed-instant:2026-09-15T11:03:00Z}") String fixedInstant
    ) {
        if (fixedInstant == null || fixedInstant.isBlank() || "SYSTEM_UTC".equalsIgnoreCase(fixedInstant.trim())) {
            return Clock.systemUTC();
        }
        return Clock.fixed(Instant.parse(fixedInstant.trim()), ZoneOffset.UTC);
    }

    @Bean
    public DisruptionDomainEngine disruptionDomainEngine() {
        return new DisruptionDomainEngine();
    }

    @Bean
    public AiSafetySandwichService aiSafetySandwichService() {
        return new AiSafetySandwichService();
    }

    @Bean
    public DisruptionOrchestratorUseCase disruptionOrchestratorUseCase(
            TripContextPort tripContextPort,
            OperationalEventsPort operationalEventsPort,
            PolicyRulesPort policyRulesPort,
            EligibleOptionsPort eligibleOptionsPort,
            RebookingGatewayPort rebookingGatewayPort,
            HumanCaseGatewayPort humanCaseGatewayPort,
            PassengerNotificationPort passengerNotificationPort,
            AiInferencePort aiInferencePort,
            DisruptionPersistencePort persistencePort,
            DisruptionDomainEngine domainEngine,
            AiSafetySandwichService aiSafetySandwichService,
            Clock evaluationClock
    ) {
        return new DisruptionOrchestratorUseCase(
                tripContextPort,
                operationalEventsPort,
                policyRulesPort,
                eligibleOptionsPort,
                rebookingGatewayPort,
                humanCaseGatewayPort,
                passengerNotificationPort,
                aiInferencePort,
                persistencePort,
                domainEngine,
                aiSafetySandwichService,
                evaluationClock
        );
    }
}
