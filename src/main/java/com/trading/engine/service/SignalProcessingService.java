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
    private final IntradayContextEngine intradayEngine;
    private final AiAnalysisService aiAnalysis;
    private final RuleEngineService ruleEngine;
    private final ExecutionAdvisoryService executionAdvisory;
    private final JournalService journalService;
    private final NotificationService notificationService;

    // Confidence threshold to proceed with execution planning
    private static final int CONFIDENCE_THRESHOLD = 60;

    public TradeSignal processWebhook(final TradingViewWebhook webhook) {
        final var signalId = UUID.randomUUID().toString();
        log.info("Processing webhook {} for {} - Event: {}",
                signalId, webhook.getSymbol(), webhook.getEvent());

        try {
            // Step 1: Build market context
            log.info("Step 1: Building market data aggregation");
            final var context = contextBuilder.buildContext(webhook);

            if (!contextBuilder.isContextValid(context)) {
                return createInvalidSignal(signalId, webhook, "Invalid market context data");
            }

            // Step 2: Build intraday context (deterministic analysis)
            log.info("Step 2: Building intraday context (deterministic)");
            final var intradayContext = intradayEngine.buildContext(webhook.getSymbol(), context);
            log.info("Intraday context: {} - Confidence: {}/100",
                    intradayContext.getOiPriceBehavior(), intradayContext.getConfidenceScore());

            // Check confidence threshold
            if (intradayContext.getConfidenceScore() < CONFIDENCE_THRESHOLD) {
                log.info("Confidence too low ({}/100), skipping signal", intradayContext.getConfidenceScore());
                return createLowConfidenceSignal(signalId, webhook, context, intradayContext);
            }

            if (!ruleEngine.quickValidation(webhook.getSession())) {
                log.info("Failed quick validation, skipping AI analysis");
                return createBlockedSignal(signalId, webhook, context, intradayContext, "Pre-validation failed");
            }

            // Step 3: AI Trade Analysis
            log.info("Step 3: Requesting AI setup assessment");
            final var assessment = aiAnalysis.analyzeContext(context);
            log.info("AI assessment: Quality {}, Alignment {}/100",
                    assessment.getSetupQuality(), assessment.getAlignmentScore());

            // Step 4: Rule validation
            log.info("Step 4: Validating against trading rules");
            final var ruleResult = ruleEngine.validateSetup(context, assessment);

            // Step 5: Generate execution plan (if rules pass)
            log.info("Step 5: Generating execution plan");
            final var direction = determineDirection(webhook.getEvent());
            ExecutionPlan executionPlan = null;

            if (ruleResult.isPassed() && !assessment.getSetupQuality().equals("C")) {
                executionPlan = aiAnalysis.generateExecutionPlan(context, intradayContext, direction);
                log.info("Execution plan: {} - {} targets",
                        executionPlan.getExecutionModel(),
                        executionPlan.getTargets() != null ? executionPlan.getTargets().size() : 0);
            }

            // Step 6: Execution advisory checklist
            log.info("Step 6: Running execution advisory checks");
            final var checklist = executionAdvisory.generateChecklist(
                    webhook.getSymbol(), context, intradayContext
            );
            log.info("Execution checklist: {} - Ready: {}",
                    checklist.getChecklistSummary(), checklist.getReadyForExecution());

            // Step 7: Create trade signal
            log.info("Step 7: Creating trade signal");
            final var signal = createTradeSignal(
                    signalId, webhook, context, intradayContext, assessment,
                    executionPlan, ruleResult, checklist, direction
            );

            // Step 8: Journal entry
            log.info("Step 8: Creating journal entry");
            journalService.createEntry(signal);

            // Step 9: Notification (only for valid, high-confidence trades)
            if ("VALID".equals(signal.getStatus())) {
                log.info("Step 9: Sending notification for confirmed trade");
                notificationService.sendSignalNotification(signal);
            } else {
                log.info("Step 9: Skipping notification - Trade not confirmed (status: {})", signal.getStatus());
            }

            log.info("Signal processing complete - Status: {}", signal.getStatus());
            return signal;

        } catch (final Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return createErrorSignal(signalId, webhook, e.getMessage());
        }
    }

    private TradeSignal createTradeSignal(final String signalId, final TradingViewWebhook webhook,
                                           final MarketContext context, final IntradayContext intradayContext,
                                           final AiAssessment assessment, final ExecutionPlan executionPlan,
                                           final RuleResult ruleResult, final ExecutionChecklist checklist,
                                           final String direction) {
        final String status;
        final String action;

        if (!ruleResult.isPassed()) {
            status = "INVALID";
            action = "Setup blocked by rules - Do not trade";
        } else if (assessment.getSetupQuality().equals("C")) {
            status = "INVALID";
            action = "Setup quality too low for consideration";
        } else if (!checklist.getReadyForExecution()) {
            status = "INVALID";
            action = "Execution checklist failed - " + checklist.getChecklistSummary();
        } else {
            status = "VALID";
            action = String.format("Execute per plan: %s - Review checklist before entry",
                    executionPlan != null ? executionPlan.getExecutionModel() : "manual");
        }

        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(direction)
                .event(webhook.getEvent())
                .marketContext(context)
                .intradayContext(intradayContext)
                .aiAssessment(assessment)
                .executionPlan(executionPlan)
                .ruleResult(ruleResult)
                .executionChecklist(checklist)
                .timestamp(LocalDateTime.now())
                .status(status)
                .action(action)
                .build();
    }

    private TradeSignal createLowConfidenceSignal(final String signalId, final TradingViewWebhook webhook,
                                                    final MarketContext context,
                                                    final IntradayContext intradayContext) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(determineDirection(webhook.getEvent()))
                .event(webhook.getEvent())
                .marketContext(context)
                .intradayContext(intradayContext)
                .timestamp(LocalDateTime.now())
                .status("INVALID")
                .action(String.format("Low confidence: %d/100 (threshold: %d)",
                        intradayContext.getConfidenceScore(), CONFIDENCE_THRESHOLD))
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
                                             final MarketContext context, final IntradayContext intradayContext,
                                             final String reason) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(determineDirection(webhook.getEvent()))
                .event(webhook.getEvent())
                .marketContext(context)
                .intradayContext(intradayContext)
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
