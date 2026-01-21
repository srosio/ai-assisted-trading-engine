package com.trading.engine.service;

import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
    private final ClaudeConfig claudeConfig;

    @Async
    public void processWebhookAsync(final TradingViewWebhook webhook) {
        try {
            log.info("Processing webhook asynchronously for {} - Strategy: {}, Event: {}, Direction: {}",
                    webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType(), webhook.getDirection());
            processWebhook(webhook);
        } catch (final Exception e) {
            log.error("Error in async webhook processing: {}", e.getMessage(), e);
            sendErrorToTelegram(webhook, e);
        }
    }

    public TradeSignal processWebhook(final TradingViewWebhook webhook) {
        final var signalId = UUID.randomUUID().toString();
        log.info("Processing webhook {} for {} - Strategy: {}, Event: {}, Direction: {}",
                signalId, webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType(), webhook.getDirection());

        try {
            // Step 1: Build market context
            log.info("Step 1: Building market data aggregation");
            final var context = contextBuilder.buildContext(webhook);

            // Continue processing even if context is invalid - we'll capture the issue in the signal
            if (!contextBuilder.isContextValid(context)) {
                log.warn("Invalid market context data - continuing with limited analysis");
            }

            // Step 2: Build intraday context (deterministic analysis)
            log.info("Step 2: Building intraday context (deterministic)");
            final var intradayContext = intradayEngine.buildContext(webhook.getSymbol(), context);
            log.info("Intraday context: {} - Confidence: {}/100",
                    intradayContext.getOiPriceBehavior(), intradayContext.getConfidenceScore());

            final var direction = mapDirectionToTradeDirection(webhook.getDirection());
            AiAssessment assessment;
            ExecutionPlan executionPlan = null;

            if (claudeConfig.isEnableSmartGating() &&
                intradayContext.getConfidenceScore() < claudeConfig.getSmartGatingThreshold()) {

                log.info("SMART GATING: Skipping AI analysis - confidence {}/100 below threshold {}/100 (30-40% cost savings)",
                        intradayContext.getConfidenceScore(), claudeConfig.getSmartGatingThreshold());

                // Use static analysis for low-confidence signals
                assessment = createLowConfidenceAssessment(intradayContext);
            } else {
                log.info("Step 3: Requesting optimized combined AI analysis (1 API call vs 2 legacy calls - 50% savings)");
                final var combinedAnalysis = aiAnalysis.analyzeCombined(context, webhook, intradayContext, direction);

                // Extract assessment and execution plan from combined result
                assessment = combinedAnalysis.toAiAssessment();
                executionPlan = combinedAnalysis.toExecutionPlan();

                log.info("AI analysis complete - Quality: {}, Model: {}",
                        assessment.getSetupQuality(),
                        executionPlan != null ? executionPlan.getExecutionModel() : "none");
            }

            // Step 3: Rule validation
            log.info("Step 3: Validating against trading rules");
            final var ruleResult = ruleEngine.validateSetup(context, assessment);

            // Step 4: Execution advisory checklist
            log.info("Step 4: Running execution advisory checks");
            final var checklist = executionAdvisory.generateChecklist(
                    webhook.getSymbol(), context, intradayContext
            );
            log.info("Execution checklist: {} - Ready: {}",
                    checklist.getChecklistSummary(), checklist.getReadyForExecution());

            // Step 5: Create trade signal
            log.info("Step 5: Creating trade signal");
            final var signal = createTradeSignal(
                    signalId, webhook, context, intradayContext, assessment,
                    executionPlan, ruleResult, checklist, direction
            );

            // Step 8: Journal entry
            log.info("Step 8: Creating journal entry");
            journalService.createEntry(signal);

            // Step 9: Notification (send once at the end with complete analysis)
            log.info("Step 9: Sending notification - Status: {}", signal.getStatus());
            notificationService.sendSignalNotification(signal);

            log.info("Signal processing complete - Status: {}", signal.getStatus());
            return signal;

        } catch (final Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            final var signal = createErrorSignal(signalId, webhook, e.getMessage());
            try {
                log.info("Sending notification for error signal");
                notificationService.sendSignalNotification(signal);
            } catch (final Exception notifyError) {
                log.error("Failed to send error notification: {}", notifyError.getMessage());
            }
            return signal;
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
                .strategy(webhook.getStrategy())
                .eventType(webhook.getEventType())
                .event(webhook.getStrategy() + " - " + webhook.getEventType()) // legacy field
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

    private AiAssessment createLowConfidenceAssessment(final IntradayContext intradayContext) {
        return AiAssessment.builder()
                .setupQuality("C")
                .riskFactors(java.util.List.of(
                        String.format("Low confidence score: %d/100", intradayContext.getConfidenceScore()),
                        "Market microstructure not favorable",
                        "Smart gating threshold not met"
                ))
                .invalidation("Setup does not meet minimum confidence criteria")
                .summary(String.format("Setup rejected by smart gating (confidence: %d/100, threshold: %d/100). " +
                        "AI analysis skipped for cost savings.",
                        intradayContext.getConfidenceScore(), claudeConfig.getSmartGatingThreshold()))
                .alignmentScore(intradayContext.getConfidenceScore())
                .keyObservation("Smart gating rejection - AI analysis not performed")
                .build();
    }

    private TradeSignal createErrorSignal(final String signalId, final TradingViewWebhook webhook, final String error) {
        return TradeSignal.builder()
                .signalId(signalId)
                .symbol(webhook.getSymbol())
                .direction(mapDirectionToTradeDirection(webhook.getDirection()))
                .strategy(webhook.getStrategy())
                .eventType(webhook.getEventType())
                .event(webhook.getStrategy() + " - " + webhook.getEventType())
                .timestamp(LocalDateTime.now())
                .status("INVALID")
                .action("System error: " + error)
                .build();
    }

    private String mapDirectionToTradeDirection(final String direction) {
        if (direction == null) {
            return "UNKNOWN";
        }
        final var directionLower = direction.toLowerCase();
        if (directionLower.equals("bullish")) {
            return "LONG";
        } else if (directionLower.equals("bearish")) {
            return "SHORT";
        }
        return "UNKNOWN";
    }

    private void sendErrorToTelegram(final TradingViewWebhook webhook, final Exception error) {
        try {
            final var errorMessage = formatErrorMessage(webhook, error);
            notificationService.sendErrorNotification(errorMessage);
        } catch (final Exception e) {
            log.error("Failed to send error notification to Telegram: {}", e.getMessage());
        }
    }

    private String formatErrorMessage(final TradingViewWebhook webhook, final Exception error) {
        final var sb = new StringBuilder();
        sb.append("🚨 <b>WEBHOOK PROCESSING ERROR</b>\n\n");
        sb.append("<b>Symbol:</b> ").append(webhook.getSymbol()).append("\n");
        sb.append("<b>Strategy:</b> ").append(webhook.getStrategy()).append("\n");
        sb.append("<b>Event Type:</b> ").append(webhook.getEventType()).append("\n");
        sb.append("<b>Direction:</b> ").append(webhook.getDirection()).append("\n");
        sb.append("<b>Session:</b> ").append(webhook.getSession()).append("\n");
        sb.append("<b>Timeframe:</b> ").append(webhook.getTimeframe()).append("\n\n");
        sb.append("<b>Error:</b> ").append(error.getClass().getSimpleName()).append("\n");
        sb.append("<b>Message:</b> ").append(error.getMessage()).append("\n\n");

        if (error.getCause() != null) {
            sb.append("<b>Cause:</b> ").append(error.getCause().getMessage()).append("\n\n");
        }

        sb.append("<b>Action Required:</b> Check logs and fix the issue\n");
        sb.append("<b>Timestamp:</b> ").append(LocalDateTime.now()).append("\n");

        return sb.toString();
    }
}
