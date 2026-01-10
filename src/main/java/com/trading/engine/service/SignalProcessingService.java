package com.trading.engine.service;

import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

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

    public TradeSignal processWebhook(final TradingViewWebhook webhook) {
        final var signalId = UUID.randomUUID().toString();
        log.info("Processing webhook {} for {} - Event: {}",
                signalId, webhook.getSymbol(), webhook.getEvent());

        try {
            log.info("Step 1: Building market context");
            final var context = contextBuilder.buildContext(webhook);

            if (!contextBuilder.isContextValid(context)) {
                return createInvalidSignal(signalId, webhook, "Invalid market context data");
            }

            if (!ruleEngine.quickValidation(webhook.getSession())) {
                log.info("Failed quick validation, skipping AI analysis");
                return createBlockedSignal(signalId, webhook, context, "Pre-validation failed");
            }

            log.info("Step 2: Requesting AI analysis");
            final var assessment = aiAnalysis.analyzeContext(context);
            log.info("AI analysis result: {}", assessment);

            log.info("Step 3: Validating against rules");
            final var ruleResult = ruleEngine.validateSetup(context, assessment);

            log.info("Step 4: Calculating risk parameters");
            final var direction = determineDirection(webhook.getEvent());
            final var riskCalc = riskEngine.calculateRisk(context, direction);

            log.info("Step 5: Creating trade signal");
            final var signal = createTradeSignal(
                    signalId, webhook, context, assessment, ruleResult, riskCalc, direction
            );

            log.info("Step 6: Creating journal entry");
            journalService.createEntry(signal);

            // Only send notification for confirmed trades (VALID status means can trade)
            if ("VALID".equals(signal.getStatus())) {
                log.info("Step 7: Sending notification for confirmed trade");
                notificationService.sendSignalNotification(signal);
            } else {
                log.info("Step 7: Skipping notification - Trade not confirmed (status: {})", signal.getStatus());
            }

            log.info("Signal processing complete - Status: {}", signal.getStatus());
            return signal;

        } catch (final Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return createErrorSignal(signalId, webhook, e.getMessage());
        }
    }

    private TradeSignal createTradeSignal(final String signalId, final TradingViewWebhook webhook,
                                           final MarketContext context, final AiAssessment assessment,
                                           final RuleResult ruleResult, final RiskCalculation riskCalc,
                                           final String direction) {
        final String status;
        final String action;

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

    private TradeSignal createInvalidSignal(final String signalId, final TradingViewWebhook webhook, final String reason) {
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

    private TradeSignal createBlockedSignal(final String signalId, final TradingViewWebhook webhook,
                                             final MarketContext context, final String reason) {
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

    private TradeSignal createErrorSignal(final String signalId, final TradingViewWebhook webhook, final String error) {
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

    private String determineDirection(final String event) {
        final var eventLower = event.toLowerCase();
        if (eventLower.contains("long") || eventLower.contains("bullish")) {
            return "LONG";
        } else if (eventLower.contains("short") || eventLower.contains("bearish")) {
            return "SHORT";
        }
        return "UNKNOWN";
    }
}
