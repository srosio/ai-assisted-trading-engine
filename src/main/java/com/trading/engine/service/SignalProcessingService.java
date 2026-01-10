package com.trading.engine.service;

import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Main orchestration service that coordinates the entire signal processing pipeline.
 *
 * Pipeline:
 * 1. Build market context from webhook + live data
 * 2. Get AI analysis (constrained)
 * 3. Validate against rules
 * 4. Calculate risk
 * 5. Create trade signal
 * 6. Journal entry
 * 7. Send notification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalProcessingService {

    private final ContextBuilderService contextBuilder;
    private final AiAnalysisService aiAnalysis;
    private final RuleEngineService ruleEngine;
    private final RiskEngineService riskEngine;
    private final JournalService journalService;
    private final NotificationService notificationService;

    /**
     * Process incoming webhook through complete pipeline
     */
    public TradeSignal processWebhook(TradingViewWebhook webhook) {
        String signalId = UUID.randomUUID().toString();
        log.info("Processing webhook {} for {} - Event: {}",
                signalId, webhook.getSymbol(), webhook.getEvent());

        try {
            // Step 1: Build market context
            log.info("Step 1: Building market context");
            MarketContext context = contextBuilder.buildContext(webhook);

            // Validate context quality
            if (!contextBuilder.isContextValid(context)) {
                return createInvalidSignal(signalId, webhook, "Invalid market context data");
            }

            // Quick validation before expensive AI call
            if (!ruleEngine.quickValidation(webhook.getSession())) {
                log.info("Failed quick validation, skipping AI analysis");
                return createBlockedSignal(signalId, webhook, context, "Pre-validation failed");
            }

            // Step 2: Get AI analysis
            log.info("Step 2: Requesting AI analysis");
            AiAssessment assessment = aiAnalysis.analyzeContext(context);

            // Step 3: Validate against rules
            log.info("Step 3: Validating against rules");
            RuleResult ruleResult = ruleEngine.validateSetup(context, assessment);

            // Step 4: Calculate risk
            log.info("Step 4: Calculating risk parameters");
            String direction = determineDirection(webhook.getEvent());
            RiskCalculation riskCalc = riskEngine.calculateRisk(context, direction);

            // Step 5: Create trade signal
            log.info("Step 5: Creating trade signal");
            TradeSignal signal = createTradeSignal(
                    signalId, webhook, context, assessment, ruleResult, riskCalc, direction
            );

            // Step 6: Journal entry
            log.info("Step 6: Creating journal entry");
            journalService.createEntry(signal);

            // Step 7: Send notification
            log.info("Step 7: Sending notification");
            notificationService.sendSignalNotification(signal);

            log.info("Signal processing complete - Status: {}", signal.getStatus());
            return signal;

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return createErrorSignal(signalId, webhook, e.getMessage());
        }
    }

    /**
     * Create complete trade signal
     */
    private TradeSignal createTradeSignal(String signalId, TradingViewWebhook webhook,
                                           MarketContext context, AiAssessment assessment,
                                           RuleResult ruleResult, RiskCalculation riskCalc,
                                           String direction) {
        // Determine status and action
        String status;
        String action;

        if (!ruleResult.isPassed()) {
            status = "INVALID";
            action = "Setup blocked by rules - Do not trade";
        } else if (!riskCalc.isWithinRiskLimits()) {
            status = "INVALID";
            action = "Risk limits violated - " + riskCalc.getLimitViolation();
        } else if (assessment.getSetupQuality().equals("C")) {
            status = "INVALID";
            action = "Setup quality too low for consideration";
        } else {
            status = "VALID";
            action = "Monitor per trading plan";
        }

        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(direction)
                .event(webhook.getEvent())
                .marketContext(context)
                .aiAssessment(assessment)
                .ruleResult(ruleResult)
                .riskCalculation(riskCalc)
                .timestamp(LocalDateTime.now())
                .status(status)
                .action(action)
                .build();
    }

    /**
     * Create invalid signal (bad data)
     */
    private TradeSignal createInvalidSignal(String signalId, TradingViewWebhook webhook, String reason) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(determineDirection(webhook.getEvent()))
                .event(webhook.getEvent())
                .timestamp(LocalDateTime.now())
                .status("INVALID")
                .action("Data quality insufficient - " + reason)
                .build();
    }

    /**
     * Create blocked signal (failed pre-validation)
     */
    private TradeSignal createBlockedSignal(String signalId, TradingViewWebhook webhook,
                                             MarketContext context, String reason) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(determineDirection(webhook.getEvent()))
                .event(webhook.getEvent())
                .marketContext(context)
                .timestamp(LocalDateTime.now())
                .status("INVALID")
                .action("Blocked: " + reason)
                .build();
    }

    /**
     * Create error signal (system error)
     */
    private TradeSignal createErrorSignal(String signalId, TradingViewWebhook webhook, String error) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(determineDirection(webhook.getEvent()))
                .event(webhook.getEvent())
                .timestamp(LocalDateTime.now())
                .status("INVALID")
                .action("System error: " + error)
                .build();
    }

    /**
     * Determine trade direction from event name
     */
    private String determineDirection(String event) {
        String eventLower = event.toLowerCase();
        if (eventLower.contains("long") || eventLower.contains("bullish")) {
            return "LONG";
        } else if (eventLower.contains("short") || eventLower.contains("bearish")) {
            return "SHORT";
        }
        return "UNKNOWN";
    }
}
