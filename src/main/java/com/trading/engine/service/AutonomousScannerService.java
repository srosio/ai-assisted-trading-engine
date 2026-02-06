package com.trading.engine.service;

import com.trading.engine.config.ClaudeConfig;
import com.trading.engine.config.ScannerConfig;
import com.trading.engine.config.TradingConfig;
import com.trading.engine.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Autonomous Scanner Service - The core of the TradingView-free trading engine.
 *
 * Runs on a schedule (London/NY session opens) and:
 * 1. Screens all watchlist symbols using Binance live data
 * 2. Sends interesting symbols to AI for setup discovery
 * 3. Validates discovered setups against trading rules
 * 4. Journals and notifies actionable setups via Telegram
 *
 * This replaces the TradingView webhook dependency entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomousScannerService {

    private final ScannerConfig scannerConfig;
    private final TradingConfig tradingConfig;
    private final ClaudeConfig claudeConfig;
    private final MarketScreenerService screener;
    private final AiSetupDiscoveryService discoveryService;
    private final IntradayContextEngine intradayEngine;
    private final RuleEngineService ruleEngine;
    private final ExecutionAdvisoryService executionAdvisory;
    private final SignalClassificationService classificationService;
    private final JournalService journalService;
    private final NotificationService notificationService;

    /**
     * Main entry point - scan all watchlist symbols for the current session.
     * Called by Lambda EventBridge schedule or manually.
     */
    public ScanSummary runSessionScan() {
        final var startTime = Instant.now();
        final var session = determineCurrentSession();

        log.info("=== AUTONOMOUS SCAN STARTED === Session: {} | Watchlist: {} symbols | Time: {}",
                session, scannerConfig.getWatchlist().size(), ZonedDateTime.now(ZoneId.of("UTC")));

        if (!scannerConfig.isEnabled()) {
            log.info("Scanner is disabled in configuration");
            return ScanSummary.builder()
                    .session(session)
                    .totalScanned(0)
                    .message("Scanner disabled")
                    .build();
        }

        final var signals = new ArrayList<TradeSignal>();
        var symbolsScanned = 0;
        var interestingCount = 0;
        var setupsFound = 0;
        var tradeableCount = 0;

        try {
            // Step 1: Pre-screen all watchlist symbols
            log.info("[Step 1/4] Pre-screening {} symbols...", scannerConfig.getWatchlist().size());
            final var interestingSymbols = screener.screenWatchlist(session);
            symbolsScanned = scannerConfig.getWatchlist().size();
            interestingCount = interestingSymbols.size();

            log.info("[Step 1/4] Pre-screening complete: {}/{} symbols interesting",
                    interestingCount, symbolsScanned);

            // Step 2: AI discovery for interesting symbols
            log.info("[Step 2/4] Running AI discovery on {} symbols...", interestingCount);
            for (final var screen : interestingSymbols) {
                try {
                    final var signal = processSymbol(screen, session);
                    if (signal != null) {
                        signals.add(signal);
                        if (signal.getAiAssessment() != null &&
                            !"C".equals(signal.getAiAssessment().getSetupQuality())) {
                            setupsFound++;
                        }
                        if (signal.getSignalType() == SignalType.TRADE) {
                            tradeableCount++;
                        }
                    }
                } catch (final Exception e) {
                    log.error("Error processing {}: {}", screen.getSymbol(), e.getMessage());
                }
            }

            log.info("[Step 3/4] Discovery complete: {} setups found, {} tradeable", setupsFound, tradeableCount);

            // Step 4: Send session summary
            if (scannerConfig.isSendSessionSummary()) {
                log.info("[Step 4/4] Sending session summary...");
                sendSessionSummary(session, symbolsScanned, interestingCount,
                        setupsFound, tradeableCount, signals, startTime);
            }

        } catch (final Exception e) {
            log.error("Scan failed: {}", e.getMessage(), e);
            sendScanError(session, e);
        }

        final var duration = Duration.between(startTime, Instant.now());
        log.info("=== AUTONOMOUS SCAN COMPLETE === Duration: {}s | Scanned: {} | Interesting: {} | Setups: {} | Tradeable: {}",
                duration.toSeconds(), symbolsScanned, interestingCount, setupsFound, tradeableCount);

        return ScanSummary.builder()
                .session(session)
                .totalScanned(symbolsScanned)
                .interestingSymbols(interestingCount)
                .setupsFound(setupsFound)
                .tradeableSetups(tradeableCount)
                .signals(signals)
                .durationMs(duration.toMillis())
                .message("Scan complete")
                .build();
    }

    /**
     * Process a single symbol through the full pipeline:
     * Screen → Context → AI Discovery → Rules → Classify → Journal → Notify
     */
    private TradeSignal processSymbol(final ScreenResult screen, final String session) {
        final var symbol = screen.getSymbol();
        final var signalId = UUID.randomUUID().toString();

        log.info("[{}] Processing {} - Interest: {}/100, Signals: {}",
                signalId.substring(0, 8), symbol, screen.getInterestScore(), screen.getSignals());

        // Build market context from screen data
        final var marketContext = buildMarketContextFromScreen(screen, session);

        // Build intraday context
        final var intradayContext = intradayEngine.buildContext(symbol, marketContext);

        // Smart gating check
        if (claudeConfig.isEnableSmartGating() &&
            intradayContext.getConfidenceScore() < claudeConfig.getSmartGatingThreshold()) {
            log.info("[{}] {} skipped - confidence {}/100 < threshold {}/100",
                    signalId.substring(0, 8), symbol,
                    intradayContext.getConfidenceScore(), claudeConfig.getSmartGatingThreshold());
            return null;
        }

        // AI setup discovery
        final var discovered = discoveryService.discoverSetup(screen, marketContext, intradayContext, session);

        if (!discovered.isSetupFound()) {
            log.info("[{}] {} - No setup found: {}", signalId.substring(0, 8), symbol, discovered.getNoSetupReason());
            return null;
        }

        // Convert discovered setup to assessment and execution plan
        final var assessment = convertToAssessment(discovered);
        final var executionPlan = convertToExecutionPlan(discovered);

        // Rule validation
        final var ruleResult = ruleEngine.validateSetup(marketContext, assessment, discovered.getEventType());

        // Execution advisory
        final var checklist = executionAdvisory.generateChecklist(symbol, marketContext, intradayContext);

        // Build trade signal
        final var signal = TradeSignal.builder()
                .signalId(signalId)
                .symbol(symbol)
                .direction(discovered.getDirection())
                .strategy(discovered.getStrategy())
                .eventType(discovered.getEventType())
                .event("SCAN - " + discovered.getStrategy() + " - " + discovered.getEventType())
                .marketContext(marketContext)
                .intradayContext(intradayContext)
                .aiAssessment(assessment)
                .executionPlan(executionPlan)
                .ruleResult(ruleResult)
                .executionChecklist(checklist)
                .timestamp(LocalDateTime.now())
                .build();

        // Classify signal
        final var signalType = classificationService.classify(signal);
        signal.setSignalType(signalType);
        setSignalStatusAndAction(signal, signalType, assessment, executionPlan, ruleResult, checklist);

        log.info("[{}] {} classified as {} - Quality: {} | Direction: {} | Strategy: {}",
                signalId.substring(0, 8), symbol, signalType,
                assessment.getSetupQuality(), discovered.getDirection(), discovered.getStrategy());

        // Journal
        try {
            journalService.createEntry(signal);
        } catch (final Exception e) {
            log.error("Failed to journal signal for {}: {}", symbol, e.getMessage());
        }

        // Notify (respect filter setting)
        if (!scannerConfig.isOnlySendTradeableSignals() || signalType == SignalType.TRADE) {
            try {
                notificationService.sendSignalNotification(signal);
            } catch (final Exception e) {
                log.error("Failed to notify for {}: {}", symbol, e.getMessage());
            }
        }

        return signal;
    }

    private MarketContext buildMarketContextFromScreen(final ScreenResult screen, final String session) {
        return MarketContext.builder()
                .symbol(screen.getSymbol())
                .htfBias(screen.getTrendBias())
                .location(screen.getPriceLocation())
                .session(session)
                .oiChangePercent(screen.getOiChangePercent())
                .fundingRate(screen.getFundingRate())
                .liquidityEvent("SCAN - " + String.join(", ", screen.getSignals()))
                .volatility(screen.getVolatilityState())
                .currentPrice(screen.getCurrentPrice())
                .previousDayHigh(screen.getPreviousDayHigh())
                .previousDayLow(screen.getPreviousDayLow())
                .volumeSpike(screen.getTakerRatio() != null && screen.getTakerRatio() > 1.15)
                .displacementDetected(screen.getInterestScore() >= 60)
                .atrValue(screen.getAtr())
                .nearestResistance(screen.getNearestResistance())
                .nearestSupport(screen.getNearestSupport())
                .build();
    }

    private AiAssessment convertToAssessment(final DiscoveredSetup setup) {
        return AiAssessment.builder()
                .setupQuality(setup.getSetupQuality())
                .riskFactors(setup.getRiskFactors())
                .invalidation(setup.getInvalidation())
                .summary(setup.getSetupDescription())
                .alignmentScore(setup.getAlignmentScore())
                .keyObservation(setup.getTriggerCondition())
                .watchCondition(setup.getWatchCondition())
                .improvementPath(setup.getImprovementPath())
                .keyLevelToWatch(setup.getKeyLevelToWatch())
                .timeframeGuidance(setup.getTimeframeGuidance())
                .build();
    }

    private ExecutionPlan convertToExecutionPlan(final DiscoveredSetup setup) {
        if (setup.getSuggestedEntry() == null) {
            return null;
        }

        return ExecutionPlan.builder()
                .executionModel(setup.getExecutionModel())
                .entryZoneLow(setup.getSuggestedEntry())
                .entryZoneHigh(setup.getSuggestedEntry())
                .stopLogic(setup.getStopLogic())
                .suggestedStopPrice(setup.getSuggestedStop())
                .targets(setup.getTargets())
                .riskNotes(setup.getRiskFactors())
                .executionNotes("Autonomous scan discovery - " + setup.getTriggerCondition())
                .build();
    }

    private void setSignalStatusAndAction(final TradeSignal signal, final SignalType type,
                                           final AiAssessment assessment, final ExecutionPlan plan,
                                           final RuleResult ruleResult, final ExecutionChecklist checklist) {
        switch (type) {
            case TRADE -> {
                signal.setStatus("VALID");
                signal.setAction(String.format("SCAN DISCOVERY: Execute %s - Review before entry",
                        plan != null ? plan.getExecutionModel() : "manual"));
            }
            case WATCH -> {
                signal.setStatus("WATCH");
                signal.setAction(assessment.getWatchCondition() != null
                        ? "Watch: " + assessment.getWatchCondition()
                        : "Monitor for improved conditions");
            }
            case BLOCKED -> {
                signal.setStatus("INVALID");
                if (!ruleResult.isPassed() && ruleResult.getFailedRules() != null && !ruleResult.getFailedRules().isEmpty()) {
                    signal.setAction("Blocked: " + ruleResult.getFailedRules().get(0));
                } else {
                    signal.setAction("Setup blocked by rules");
                }
            }
        }
    }

    private void sendSessionSummary(final String session, final int scanned, final int interesting,
                                     final int setups, final int tradeable,
                                     final List<TradeSignal> signals, final Instant startTime) {
        final var duration = Duration.between(startTime, Instant.now());
        final var sb = new StringBuilder();

        sb.append("📡 <b>SESSION SCAN COMPLETE</b>\n\n");
        sb.append("<b>Session:</b> ").append(session).append("\n");
        sb.append("<b>Time:</b> ").append(ZonedDateTime.now(ZoneId.of("UTC")).toLocalTime()).append(" UTC\n");
        sb.append("<b>Duration:</b> ").append(duration.toSeconds()).append("s\n\n");

        sb.append("<b>📊 SCAN RESULTS</b>\n");
        sb.append("Symbols scanned: ").append(scanned).append("\n");
        sb.append("Interesting: ").append(interesting).append("\n");
        sb.append("Setups found: ").append(setups).append("\n");
        sb.append("Tradeable: ").append(tradeable).append("\n\n");

        if (!signals.isEmpty()) {
            sb.append("<b>🎯 DISCOVERED SETUPS</b>\n");
            for (final var signal : signals) {
                final var emoji = switch (signal.getSignalType()) {
                    case TRADE -> "🟢";
                    case WATCH -> "👀";
                    case BLOCKED -> "🚫";
                };
                final var sym = "$" + signal.getSymbol().replace("USDT", "");
                sb.append(emoji).append(" ").append(sym)
                  .append(" ").append(signal.getDirection())
                  .append(" | ").append(signal.getStrategy())
                  .append(" | Quality: ").append(signal.getAiAssessment() != null ?
                          signal.getAiAssessment().getSetupQuality() : "?")
                  .append("\n");
            }
        } else {
            sb.append("💤 <i>No actionable setups found this session.</i>\n");
            sb.append("Markets are quiet - next scan at session open.\n");
        }

        sb.append("\n<b>Watchlist:</b> ").append(String.join(", ",
                scannerConfig.getWatchlist().stream()
                        .map(s -> "$" + s.replace("USDT", ""))
                        .toList()));

        notificationService.sendErrorNotification(sb.toString()); // Reuse error notification for raw HTML
    }

    private void sendScanError(final String session, final Exception error) {
        final var message = String.format(
                "🚨 <b>SCAN ERROR</b>\n\n" +
                "<b>Session:</b> %s\n" +
                "<b>Error:</b> %s\n" +
                "<b>Message:</b> %s\n\n" +
                "⚠️ Check CloudWatch logs for details",
                session, error.getClass().getSimpleName(), error.getMessage());

        try {
            notificationService.sendErrorNotification(message);
        } catch (final Exception e) {
            log.error("Failed to send scan error notification: {}", e.getMessage());
        }
    }

    private String determineCurrentSession() {
        final var utcHour = ZonedDateTime.now(ZoneId.of("UTC")).getHour();

        if (utcHour >= 13 && utcHour < 21) {
            return "NY";
        } else if (utcHour >= 8 && utcHour < 16) {
            return "LONDON";
        } else {
            return "ASIA";
        }
    }

    /**
     * Summary DTO returned by the scan function.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ScanSummary {
        private String session;
        private int totalScanned;
        private int interestingSymbols;
        private int setupsFound;
        private int tradeableSetups;
        private List<TradeSignal> signals;
        private long durationMs;
        private String message;
    }
}
