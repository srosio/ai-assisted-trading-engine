package com.trading.engine.service;

import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Signal Processing Service - Orchestrates the 9-step trading signal pipeline
 * 
 * Pipeline:
 * 1. Market Context Building (Binance data)
 * 2. Intraday Context Engine (deterministic analysis)
 * 3. AI Analysis (with smart gating & caching)
 * 4. Rule Validation (non-negotiable rules)
 * 5. Execution Planning (entry/stop/targets)
 * 6. Execution Advisory (pre-trade checklist)
 * 7. Signal Creation (VALID/INVALID status)
 * 8. Journal Persistence (DynamoDB)
 * 9. Telegram Notification (VALID signals only)
 */
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
    private final SignalClassificationService classificationService;
    private final ClaudeConfig claudeConfig;

    /**
     * Async webhook processing for non-blocking API responses
     */
    @Async
    public void processWebhookAsync(final TradingViewWebhook webhook) {
        final var startTime = Instant.now();
        try {
            log.info("[ASYNC] Processing webhook for {} - Strategy: {}, Event: {}, Direction: {}",
                    webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType(), webhook.getDirection());
            processWebhook(webhook);
            final var duration = Duration.between(startTime, Instant.now());
            log.info("[ASYNC] Webhook processing completed in {}ms", duration.toMillis());
        } catch (final Exception e) {
            log.error("[ASYNC] Error in webhook processing: {}", e.getMessage(), e);
            sendErrorToTelegram(webhook, e);
        }
    }

    /**
     * Main webhook processing pipeline - 9 steps from webhook to notification
     */
    public TradeSignal processWebhook(final TradingViewWebhook webhook) {
        final var signalId = UUID.randomUUID().toString();
        final var startTime = Instant.now();
        
        log.info("[{}] Starting signal pipeline for {} - Strategy: {}, Event: {}, Direction: {}",
                signalId.substring(0, 8), webhook.getSymbol(), webhook.getStrategy(), 
                webhook.getEventType(), webhook.getDirection());

        try {
            // Step 1: Build market context from Binance
            final var context = buildMarketContext(webhook);
            
            // Step 2: Build intraday context (deterministic analysis)
            final var intradayContext = buildIntradayContext(webhook.getSymbol(), context);
            
            // Step 3: AI Analysis (with smart gating)
            final var direction = mapDirectionToTradeDirection(webhook.getDirection());
            final var aiResult = performAiAnalysis(context, webhook, intradayContext, direction);
            
            // Step 4: Rule validation (pass eventType for reversal counter-trend handling)
            final var ruleResult = validateRules(context, aiResult.assessment, webhook.getEventType());
            
            // Step 5: Execution advisory
            final var checklist = generateAdvisory(webhook.getSymbol(), context, intradayContext);
            
            // Step 6: Create trade signal
            final var signal = createTradeSignal(
                    signalId, webhook, context, intradayContext, 
                    aiResult.assessment, aiResult.executionPlan, 
                    ruleResult, checklist, direction
            );
            
            // Step 7: Journal persistence
            persistToJournal(signal);
            
            // Step 8: Send notification
            sendNotification(signal);
            
            logPipelineCompletion(signalId, signal, startTime);
            return signal;

        } catch (final Exception e) {
            return handlePipelineError(signalId, webhook, e, startTime);
        }
    }
    
    /**
     * Step 1: Build market context from Binance data
     */
    private MarketContext buildMarketContext(final TradingViewWebhook webhook) {
        log.debug("[Step 1] Building market context from Binance");
        final var context = contextBuilder.buildContext(webhook);
        
        if (!contextBuilder.isContextValid(context)) {
            log.warn("[Step 1] Invalid market context - continuing with limited data");
        }
        
        return context;
    }
    
    /**
     * Step 2: Build intraday context (deterministic analysis)
     */
    private IntradayContext buildIntradayContext(final String symbol, final MarketContext context) {
        log.debug("[Step 2] Building intraday context (deterministic)");
        final var intradayContext = intradayEngine.buildContext(symbol, context);
        
        log.info("[Step 2] Intraday: {} | Confidence: {}/100 | Trends: {}/{}/{}",
                intradayContext.getOiPriceBehavior(),
                intradayContext.getConfidenceScore(),
                intradayContext.getTrendBias15m(),
                intradayContext.getTrendBias5m(),
                intradayContext.getTrendBias1m());
        
        return intradayContext;
    }
    
    /**
     * Step 3: AI Analysis with smart gating
     */
    private AiResult performAiAnalysis(final MarketContext context, 
                                       final TradingViewWebhook webhook,
                                       final IntradayContext intradayContext, 
                                       final String direction) {
        // Smart gating: Skip AI for low-confidence signals
        if (shouldSkipAiAnalysis(intradayContext)) {
            log.info("[Step 3] SMART GATING: Skipping AI - confidence {}/100 < threshold {}/100 (30-40% cost savings)",
                    intradayContext.getConfidenceScore(), claudeConfig.getSmartGatingThreshold());
            return new AiResult(createLowConfidenceAssessment(intradayContext), null);
        }
        
        log.debug("[Step 3] Requesting AI analysis (combined call - 50% cost savings)");
        final var combinedAnalysis = aiAnalysis.analyzeCombined(context, webhook, intradayContext, direction);
        
        final var assessment = combinedAnalysis.toAiAssessment();
        final var executionPlan = combinedAnalysis.toExecutionPlan();
        
        log.info("[Step 3] AI complete - Quality: {} | Alignment: {}/100 | Model: {}",
                assessment.getSetupQuality(),
                assessment.getAlignmentScore(),
                executionPlan != null ? executionPlan.getExecutionModel() : "none");
        
        return new AiResult(assessment, executionPlan);
    }
    
    /**
     * Step 4: Validate against trading rules
     */
    private RuleResult validateRules(final MarketContext context, final AiAssessment assessment, final String eventType) {
        log.debug("[Step 4] Validating trading rules for eventType: {}", eventType);
        final var ruleResult = ruleEngine.validateSetup(context, assessment, eventType);

        log.info("[Step 4] Rules: {} | Passed: {}/{}",
                ruleResult.isPassed() ? "PASSED" : "FAILED",
                ruleResult.getPassedRules() != null ? ruleResult.getPassedRules().size() : 0,
                (ruleResult.getPassedRules() != null ? ruleResult.getPassedRules().size() : 0) +
                (ruleResult.getFailedRules() != null ? ruleResult.getFailedRules().size() : 0));

        return ruleResult;
    }
    
    /**
     * Step 5: Generate execution advisory checklist
     */
    private ExecutionChecklist generateAdvisory(final String symbol, 
                                                 final MarketContext context,
                                                 final IntradayContext intradayContext) {
        log.debug("[Step 5] Generating execution advisory");
        final var checklist = executionAdvisory.generateChecklist(symbol, context, intradayContext);
        
        log.info("[Step 5] Advisory: {} | Ready: {}",
                checklist.getChecklistSummary(),
                checklist.getReadyForExecution());
        
        return checklist;
    }
    
    /**
     * Step 7: Persist signal to journal
     */
    private void persistToJournal(final TradeSignal signal) {
        log.debug("[Step 7] Persisting to journal");
        try {
            journalService.createEntry(signal);
            log.info("[Step 7] Journal entry created - ID: {}", signal.getSignalId());
        } catch (final Exception e) {
            log.error("[Step 7] Failed to persist journal entry: {}", e.getMessage());
            // Don't fail the entire pipeline if journaling fails
        }
    }
    
    /**
     * Step 8: Send Telegram notification
     */
    private void sendNotification(final TradeSignal signal) {
        log.debug("[Step 8] Sending notification");
        try {
            notificationService.sendSignalNotification(signal);
            log.info("[Step 8] Notification sent - Status: {}", signal.getStatus());
        } catch (final Exception e) {
            log.error("[Step 8] Failed to send notification: {}", e.getMessage());
            // Don't fail the entire pipeline if notification fails
        }
    }
    
    /**
     * Check if AI analysis should be skipped (smart gating)
     */
    private boolean shouldSkipAiAnalysis(final IntradayContext intradayContext) {
        return claudeConfig.isEnableSmartGating() &&
               intradayContext.getConfidenceScore() < claudeConfig.getSmartGatingThreshold();
    }
    
    /**
     * Log pipeline completion with metrics
     */
    private void logPipelineCompletion(final String signalId, final TradeSignal signal, final Instant startTime) {
        final var duration = Duration.between(startTime, Instant.now());
        log.info("[{}] Pipeline complete - Status: {} | Duration: {}ms | Quality: {} | Action: {}",
                signalId.substring(0, 8),
                signal.getStatus(),
                duration.toMillis(),
                signal.getAiAssessment() != null ? signal.getAiAssessment().getSetupQuality() : "N/A",
                signal.getAction());
    }
    
    /**
     * Handle pipeline errors gracefully
     */
    private TradeSignal handlePipelineError(final String signalId, 
                                             final TradingViewWebhook webhook,
                                             final Exception e, 
                                             final Instant startTime) {
        final var duration = Duration.between(startTime, Instant.now());
        log.error("[{}] Pipeline error after {}ms: {}", 
                signalId.substring(0, 8), duration.toMillis(), e.getMessage(), e);
        
        final var signal = createErrorSignal(signalId, webhook, e.getMessage());
        
        try {
            journalService.createEntry(signal);
        } catch (final Exception journalError) {
            log.error("Failed to journal error signal: {}", journalError.getMessage());
        }
        
        try {
            notificationService.sendSignalNotification(signal);
        } catch (final Exception notifyError) {
            log.error("Failed to send error notification: {}", notifyError.getMessage());
        }
        
        return signal;
    }
    
    /**
     * Helper record for AI analysis results
     */
    private record AiResult(AiAssessment assessment, ExecutionPlan executionPlan) {}

    private TradeSignal createTradeSignal(final String signalId, final TradingViewWebhook webhook,
                                           final MarketContext context, final IntradayContext intradayContext,
                                           final AiAssessment assessment, final ExecutionPlan executionPlan,
                                           final RuleResult ruleResult, final ExecutionChecklist checklist,
                                           final String direction) {
        // Build initial signal
        final var signal = TradeSignal.builder()
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
                .build();

        // Classify signal into TRADE/WATCH/BLOCKED
        final var signalType = classificationService.classify(signal);
        signal.setSignalType(signalType);

        // Set status and action based on classification
        switch (signalType) {
            case TRADE -> {
                signal.setStatus("VALID");
                signal.setAction(String.format("Execute per plan: %s - Review checklist before entry",
                        executionPlan != null ? executionPlan.getExecutionModel() : "manual"));
            }
            case WATCH -> {
                signal.setStatus("WATCH");
                signal.setAction(buildWatchAction(assessment, ruleResult, checklist));
            }
            case BLOCKED -> {
                signal.setStatus("INVALID");
                signal.setAction(buildBlockedAction(assessment, ruleResult, checklist));
            }
        }

        log.info("[Step 6] Signal created - Type: {} | Status: {} | Quality: {}",
                signalType, signal.getStatus(), assessment.getSetupQuality());

        return signal;
    }

    private String buildWatchAction(final AiAssessment assessment, final RuleResult ruleResult,
                                     final ExecutionChecklist checklist) {
        if (assessment.getWatchCondition() != null) {
            return "Watch: " + assessment.getWatchCondition();
        }
        if (!ruleResult.isPassed() && ruleResult.getFailedRules() != null && !ruleResult.getFailedRules().isEmpty()) {
            return "Monitor - Rule issue: " + ruleResult.getFailedRules().get(0);
        }
        if (assessment.getImprovementPath() != null) {
            return "Monitor - " + assessment.getImprovementPath();
        }
        return "Monitor for improved conditions";
    }

    private String buildBlockedAction(final AiAssessment assessment, final RuleResult ruleResult,
                                       final ExecutionChecklist checklist) {
        if (!ruleResult.isPassed() && ruleResult.getFailedRules() != null && !ruleResult.getFailedRules().isEmpty()) {
            return "Blocked: " + ruleResult.getFailedRules().get(0);
        }
        if (checklist != null && checklist.getBlockers() != null && !checklist.getBlockers().isEmpty()) {
            return "Blocked: " + checklist.getBlockers().get(0);
        }
        if ("C".equals(assessment.getSetupQuality())) {
            return "Setup quality too low - Does not meet criteria";
        }
        return "Setup blocked by rules - Do not trade";
    }

    /**
     * Create assessment for low-confidence signals (smart gating)
     */
    private AiAssessment createLowConfidenceAssessment(final IntradayContext intradayContext) {
        return AiAssessment.builder()
                .setupQuality("C")
                .riskFactors(List.of(
                        String.format("Low confidence: %d/100 (threshold: %d/100)",
                                intradayContext.getConfidenceScore(),
                                claudeConfig.getSmartGatingThreshold()),
                        "Market microstructure: " + intradayContext.getOiPriceBehavior(),
                        "Volume confirmation: " + intradayContext.getVolumeConfirmation()
                ))
                .invalidation("Confidence threshold not met")
                .summary(String.format("Smart gating rejection (confidence: %d/100 < %d/100). " +
                        "Market conditions: %s. AI analysis skipped for cost optimization.",
                        intradayContext.getConfidenceScore(),
                        claudeConfig.getSmartGatingThreshold(),
                        intradayContext.getContextSummary()))
                .alignmentScore(intradayContext.getConfidenceScore())
                .keyObservation(String.format("Smart gating: %s | Trends: %s/%s",
                        intradayContext.getSessionNarrative(),
                        intradayContext.getTrendBias15m(),
                        intradayContext.getTrendBias5m()))
                // Preparation guidance for low-confidence signals
                .watchCondition("Wait for confidence to improve above " + claudeConfig.getSmartGatingThreshold())
                .improvementPath("Setup may improve when market microstructure aligns")
                .timeframeGuidance("Re-assess on next signal or after market shift")
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

    /**
     * Format error message for Telegram notification
     */
    private String formatErrorMessage(final TradingViewWebhook webhook, final Exception error) {
        return String.format(
                "🚨 <b>PIPELINE ERROR</b>\n\n" +
                "<b>Symbol:</b> %s\n" +
                "<b>Strategy:</b> %s - %s\n" +
                "<b>Direction:</b> %s | <b>Session:</b> %s\n" +
                "<b>Timeframe:</b> %s\n\n" +
                "<b>Error:</b> %s\n" +
                "<b>Message:</b> %s\n%s" +
                "<b>Time:</b> %s\n\n" +
                "⚠️ Check CloudWatch logs for details",
                webhook.getSymbol(),
                webhook.getStrategy(),
                webhook.getEventType(),
                webhook.getDirection(),
                webhook.getSession(),
                webhook.getTimeframe(),
                error.getClass().getSimpleName(),
                error.getMessage(),
                error.getCause() != null ? "<b>Cause:</b> " + error.getCause().getMessage() + "\n" : "",
                LocalDateTime.now()
        );
    }
}
