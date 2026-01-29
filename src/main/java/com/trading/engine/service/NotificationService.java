package com.trading.engine.service;

import com.trading.engine.config.TelegramConfig;
import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.IntradayContext;
import com.trading.engine.domain.SignalType;
import com.trading.engine.domain.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService extends TelegramLongPollingBot {

    private final TelegramConfig telegramConfig;

    public void sendSignalNotification(final TradeSignal signal) {
        if (!telegramConfig.getEnabled()) {
            log.debug("Telegram notifications disabled");
            return;
        }

        // Only send notifications for actionable TRADE signals
        final var signalType = signal.getSignalType();
        if (signalType != null && signalType != SignalType.TRADE) {
            log.debug("Skipping notification for non-actionable signal type: {}", signalType);
            return;
        }

        // Also skip legacy INVALID signals
        if (signalType == null && !"VALID".equals(signal.getStatus())) {
            log.debug("Skipping notification for invalid signal status: {}", signal.getStatus());
            return;
        }

        final var message = formatSignalMessage(signal);
        sendMessage(message);
    }

    public void sendErrorNotification(final String errorMessage) {
        if (!telegramConfig.getEnabled()) {
            log.debug("Telegram notifications disabled");
            return;
        }

        sendMessage(errorMessage);
    }

    private String formatSignalMessage(final TradeSignal signal) {
        final var signalType = signal.getSignalType();

        // Use new three-tier system if signalType is set
        if (signalType != null) {
            return switch (signalType) {
                case TRADE -> formatValidSignal(signal);
                case WATCH -> formatWatchSignal(signal);
                case BLOCKED -> formatBlockedSignal(signal);
            };
        }

        // Fallback to legacy logic
        final var isValid = "VALID".equals(signal.getStatus());
        if (isValid) {
            return formatValidSignal(signal);
        } else {
            return formatInvalidSignal(signal);
        }
    }
    
    /**
     * Format VALID signal - actionable trade setup
     */
    private String formatValidSignal(final TradeSignal signal) {
        final var sb = new StringBuilder();
        final var ctx = signal.getMarketContext();
        final var ai = signal.getAiAssessment();
        final var plan = signal.getExecutionPlan();
        
        // Header
        sb.append("🟢 <b>TRADE SETUP - ").append(getQualityEmoji(ai)).append(" QUALITY</b>\n\n");
        
        // Symbol & Direction
        final var symbol = "$" + signal.getSymbol().replace("USDT", "");
        sb.append("<b>").append(symbol).append(" ").append(signal.getDirection()).append("</b>\n");
        sb.append(signal.getStrategy()).append(" • ").append(signal.getEventType());
        if (ctx != null) {
            sb.append(" • ").append(ctx.getSession());
        }
        sb.append("\n\n");
        
        // Price & Execution
        sb.append("<b>💰 EXECUTION</b>\n");
        if (ctx != null) {
            sb.append("Current: <b>").append(ctx.getCurrentPrice()).append("</b>\n");
        }
        if (plan != null) {
            if (plan.getEntryZoneLow() != null && plan.getEntryZoneHigh() != null) {
                sb.append("Entry: ").append(plan.getEntryZoneLow())
                  .append(" - ").append(plan.getEntryZoneHigh());
                if (plan.getExecutionModel() != null) {
                    sb.append(" (").append(plan.getExecutionModel()).append(")");
                }
                sb.append("\n");
            }
            if (plan.getSuggestedStopPrice() != null) {
                sb.append("Stop Loss: <b>").append(plan.getSuggestedStopPrice()).append("</b>");
                if (ctx != null && ctx.getCurrentPrice() != null) {
                    final var risk = calculateRiskPercent(ctx.getCurrentPrice(), plan.getSuggestedStopPrice());
                    sb.append(" (-").append(String.format("%.1f", risk)).append("%)");
                }
                sb.append("\n");
            }
            if (plan.getTargets() != null && !plan.getTargets().isEmpty()) {
                sb.append("Targets:\n");
                for (int i = 0; i < Math.min(3, plan.getTargets().size()); i++) {
                    final var target = plan.getTargets().get(i);
                    sb.append("  T").append(i + 1).append(": ").append(target.getPrice());
                    if (target.getRMultiple() != null) {
                        sb.append(" (").append(String.format("%.1f", target.getRMultiple())).append("R)");
                    }
                    sb.append("\n");
                }
            }
        }
        sb.append("\n");
        
        // Market Insight
        sb.append("<b>📊 MARKET INSIGHT</b>\n");
        if (signal.getIntradayContext() != null) {
            final var intraday = signal.getIntradayContext();
            sb.append("Confidence: <b>").append(intraday.getConfidenceScore()).append("/100</b>\n");
            sb.append("Trends: ").append(formatTrends(intraday)).append("\n");
            sb.append("Setup: ").append(formatOiBehavior(intraday.getOiPriceBehavior())).append("\n");
        }
        if (ctx != null) {
            if (ctx.getOiChangePercent() != null) {
                sb.append("OI: ").append(formatOiChange(ctx.getOiChangePercent())).append("\n");
            }
            if (ctx.getFundingRate() != null) {
                sb.append("Funding: ").append(formatFunding(ctx.getFundingRate())).append("\n");
            }
            if (ctx.getVolatility() != null) {
                sb.append("Volatility: ").append(ctx.getVolatility()).append("\n");
            }
        }
        sb.append("\n");
        
        // AI Assessment
        if (ai != null) {
            sb.append("<b>🤖 AI ASSESSMENT</b>\n");
            sb.append("Quality: <b>").append(ai.getSetupQuality()).append("</b> | ");
            sb.append("Alignment: ").append(ai.getAlignmentScore()).append("/100\n");
            if (ai.getKeyObservation() != null) {
                sb.append("💡 ").append(ai.getKeyObservation()).append("\n");
            }
        }
        sb.append("\n");
        
        // Warnings
        if (signal.getExecutionChecklist() != null) {
            final var warnings = signal.getExecutionChecklist().getWarnings();
            if (warnings != null && !warnings.isEmpty()) {
                sb.append("<b>⚠️ WARNINGS</b>\n");
                for (final var warning : warnings) {
                    sb.append("• ").append(warning).append("\n");
                }
                sb.append("\n");
            }
        }
        
        // Action
        sb.append("<b>✅ ACTION: READY TO TRADE</b>\n");
        sb.append("Review execution plan and enter position\n");
        
        return sb.toString();
    }
    
    /**
     * Format INVALID signal - rejected setup
     */
    private String formatInvalidSignal(final TradeSignal signal) {
        final var sb = new StringBuilder();
        final var ctx = signal.getMarketContext();
        final var ai = signal.getAiAssessment();
        
        // Header
        sb.append("🔴 <b>SETUP REJECTED</b>\n\n");
        
        // Symbol & Direction
        final var symbol = "$" + signal.getSymbol().replace("USDT", "");
        sb.append("<b>").append(symbol).append(" ").append(signal.getDirection()).append("</b>\n");
        sb.append(signal.getStrategy()).append(" • ").append(signal.getEventType());
        if (ctx != null) {
            sb.append(" • ").append(ctx.getSession());
        }
        sb.append("\n\n");
        
        // Rejection Reason
        sb.append("<b>❌ REASON</b>\n");
        sb.append(signal.getAction()).append("\n\n");
        
        // Quality Info
        if (ai != null) {
            sb.append("<b>📊 DETAILS</b>\n");
            sb.append("Quality: <b>").append(ai.getSetupQuality()).append("</b>\n");
            if (signal.getIntradayContext() != null) {
                sb.append("Confidence: ").append(signal.getIntradayContext().getConfidenceScore()).append("/100\n");
            }
            if (ctx != null) {
                if (ctx.getOiChangePercent() != null) {
                    sb.append("OI: ").append(formatOiChange(ctx.getOiChangePercent())).append("\n");
                }
                if (ctx.getFundingRate() != null) {
                    sb.append("Funding: ").append(formatFunding(ctx.getFundingRate())).append("\n");
                }
            }
            if (ai.getRiskFactors() != null && !ai.getRiskFactors().isEmpty()) {
                sb.append("\n<b>Risk Factors:</b>\n");
                for (int i = 0; i < Math.min(3, ai.getRiskFactors().size()); i++) {
                    sb.append("• ").append(ai.getRiskFactors().get(i)).append("\n");
                }
            }
        }
        sb.append("\n");
        
        // Blockers
        if (signal.getExecutionChecklist() != null) {
            final var blockers = signal.getExecutionChecklist().getBlockers();
            if (blockers != null && !blockers.isEmpty()) {
                sb.append("<b>🚫 BLOCKERS</b>\n");
                for (final var blocker : blockers) {
                    sb.append("• ").append(blocker).append("\n");
                }
                sb.append("\n");
            }
        }
        
        sb.append("<b>⛔ ACTION: DO NOT TRADE</b>\n");

        return sb.toString();
    }

    /**
     * Format WATCH signal - setup with potential, needs monitoring
     */
    private String formatWatchSignal(final TradeSignal signal) {
        final var sb = new StringBuilder();
        final var ctx = signal.getMarketContext();
        final var ai = signal.getAiAssessment();
        final var intraday = signal.getIntradayContext();

        // Header
        sb.append("👀 <b>WATCH SETUP</b>\n\n");

        // Symbol & Direction
        final var symbol = "$" + signal.getSymbol().replace("USDT", "");
        sb.append("<b>").append(symbol).append(" ").append(signal.getDirection()).append("</b>\n");
        sb.append(signal.getStrategy()).append(" • ").append(signal.getEventType());
        if (ctx != null && ctx.getSession() != null) {
            sb.append(" • ").append(ctx.getSession());
        }
        sb.append("\n\n");

        // Current Status
        sb.append("<b>📍 STATUS</b>\n");
        if (ai != null) {
            sb.append("Quality: <b>").append(ai.getSetupQuality()).append("</b>");
            if (ai.getImprovementPath() != null) {
                sb.append(" → Can improve");
            }
            sb.append("\n");
        }
        if (intraday != null && intraday.getConfidenceScore() != null) {
            sb.append("Confidence: ").append(intraday.getConfidenceScore()).append("/100\n");
        }
        if (signal.getAction() != null) {
            sb.append("Issue: ").append(truncate(signal.getAction(), 60)).append("\n");
        }
        sb.append("\n");

        // Market Context
        sb.append("<b>📊 CONTEXT</b>\n");
        if (ctx != null) {
            if (ctx.getHtfBias() != null) {
                sb.append("HTF Bias: <b>").append(ctx.getHtfBias()).append("</b>\n");
            }
            if (ctx.getCurrentPrice() != null) {
                sb.append("Price: ").append(ctx.getCurrentPrice()).append("\n");
            }
            if (ctx.getOiChangePercent() != null) {
                sb.append("OI: ").append(formatOiChange(ctx.getOiChangePercent())).append("\n");
            }
            if (ctx.getFundingRate() != null) {
                sb.append("Funding: ").append(formatFunding(ctx.getFundingRate())).append("\n");
            }
        }

        // HTF Conflict Analysis (if present)
        if (ai != null && ai.getHtfConflictExplanation() != null) {
            sb.append("\n<b>⚠️ CONFLICT</b>\n");
            sb.append(ai.getHtfConflictExplanation()).append("\n");
        }

        // Key Level
        if (ai != null && ai.getKeyLevelToWatch() != null) {
            sb.append("\n<b>🎯 KEY LEVEL</b>\n");
            sb.append(ai.getKeyLevelToWatch()).append("\n");
        }
        sb.append("\n");

        // Watch For
        sb.append("<b>🔄 WATCH FOR</b>\n");
        if (ai != null && ai.getWatchCondition() != null) {
            sb.append(ai.getWatchCondition()).append("\n");
        }
        if (ai != null && ai.getImprovementPath() != null) {
            sb.append("\n<i>").append(ai.getImprovementPath()).append("</i>\n");
        }
        sb.append("\n");

        // Preparation Steps
        if (ai != null && ai.getPreparationSteps() != null && !ai.getPreparationSteps().isEmpty()) {
            sb.append("<b>📝 PREPARATION</b>\n");
            for (final var step : ai.getPreparationSteps()) {
                sb.append("• ").append(step).append("\n");
            }
            sb.append("\n");
        }

        // Alternative Entry (if provided)
        if (ai != null && ai.getAlternativeEntry() != null) {
            sb.append("<b>💡 ALTERNATIVE</b>\n");
            sb.append(ai.getAlternativeEntry()).append("\n\n");
        }

        // Timeframe
        if (ai != null && ai.getTimeframeGuidance() != null) {
            sb.append("<b>⏰ RE-ASSESS</b>\n");
            sb.append(ai.getTimeframeGuidance()).append("\n\n");
        }

        // Action
        sb.append("<b>⚡ ACTION: PREPARE & MONITOR</b>\n");
        sb.append("Not ready - conditions can improve\n");

        return sb.toString();
    }

    /**
     * Format BLOCKED signal - hard rule violation
     */
    private String formatBlockedSignal(final TradeSignal signal) {
        final var sb = new StringBuilder();
        final var ctx = signal.getMarketContext();
        final var ai = signal.getAiAssessment();

        // Header
        sb.append("🚫 <b>SETUP BLOCKED</b>\n\n");

        // Symbol & Direction
        final var symbol = "$" + signal.getSymbol().replace("USDT", "");
        sb.append("<b>").append(symbol).append(" ").append(signal.getDirection()).append("</b>\n");
        sb.append(signal.getStrategy()).append(" • ").append(signal.getEventType());
        if (ctx != null && ctx.getSession() != null) {
            sb.append(" • ").append(ctx.getSession());
        }
        sb.append("\n\n");

        // Hard Block Reason
        sb.append("<b>❌ HARD BLOCK</b>\n");
        sb.append(signal.getAction()).append("\n\n");

        // Brief Context
        sb.append("<b>📊 CONTEXT</b>\n");
        if (ai != null) {
            sb.append("Quality: ").append(ai.getSetupQuality()).append("\n");
        }
        if (signal.getIntradayContext() != null && signal.getIntradayContext().getConfidenceScore() != null) {
            sb.append("Confidence: ").append(signal.getIntradayContext().getConfidenceScore()).append("/100\n");
        }

        // Learning note - why this failed
        if (ai != null && ai.getRiskFactors() != null && !ai.getRiskFactors().isEmpty()) {
            sb.append("\n<b>📚 FACTORS</b>\n");
            for (int i = 0; i < Math.min(2, ai.getRiskFactors().size()); i++) {
                sb.append("• ").append(ai.getRiskFactors().get(i)).append("\n");
            }
        }

        sb.append("\n<b>⛔ ACTION: DO NOT TRADE</b>\n");
        sb.append("Rule violation - no override\n");

        return sb.toString();
    }

    // Helper for truncating long strings
    private String truncate(final String text, final int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // Helper methods for formatting
    
    private String getQualityEmoji(final AiAssessment ai) {
        if (ai == null) return "❓";
        return switch (ai.getSetupQuality()) {
            case "A" -> "🟢 A";
            case "B" -> "🟡 B";
            case "C" -> "🔴 C";
            default -> "❓";
        };
    }
    
    private double calculateRiskPercent(final BigDecimal currentPrice, final BigDecimal stopPrice) {
        if (currentPrice == null || stopPrice == null) return 0.0;
        return Math.abs(currentPrice.subtract(stopPrice)
                .divide(currentPrice, 4, java.math.RoundingMode.HALF_UP)
                .doubleValue() * 100);
    }
    
    private String formatTrends(final IntradayContext intraday) {
        final var t15 = formatTrend(intraday.getTrendBias15m());
        final var t5 = formatTrend(intraday.getTrendBias5m());
        final var t1 = formatTrend(intraday.getTrendBias1m());
        return t15 + "/" + t5 + "/" + t1 + " (15m/5m/1m)";
    }
    
    private String formatTrend(final String trend) {
        if (trend == null) return "?";
        return switch (trend.toLowerCase()) {
            case "bullish" -> "🟢";
            case "bearish" -> "🔴";
            case "neutral" -> "⚪";
            default -> "?";
        };
    }
    
    private String formatOiBehavior(final String behavior) {
        if (behavior == null) return "Unknown";
        return switch (behavior.toLowerCase()) {
            case "long_buildup" -> "Long Buildup 📈";
            case "short_buildup" -> "Short Buildup 📉";
            case "long_squeeze" -> "Long Squeeze 💥";
            case "short_squeeze" -> "Short Squeeze 🚀";
            default -> behavior.replace("_", " ");
        };
    }
    
    private String formatOiChange(final Double oiChange) {
        if (oiChange == null) return "N/A";
        final var formatted = String.format("%+.1f%%", oiChange);
        if (oiChange > 5.0) return formatted + " 🔥";
        if (oiChange < -5.0) return formatted + " ❄️";
        return formatted;
    }
    
    private String formatFunding(final Double funding) {
        if (funding == null) return "N/A";
        final var percent = funding * 100;
        final var formatted = String.format("%+.4f%%", percent);
        if (Math.abs(percent) > 0.1) return formatted + " ⚠️";
        return formatted;
    }

    private void sendMessage(final String text) {
        final var message = new SendMessage();
        message.setChatId(telegramConfig.getChatId());
        message.setText(text);
        message.setParseMode("HTML");

        var attempts = 0;
        while (attempts < telegramConfig.getRetryAttempts()) {
            try {
                execute(message);
                log.info("Telegram notification sent successfully");
                return;
            } catch (final TelegramApiException e) {
                attempts++;
                log.error("Failed to send Telegram notification (attempt {}): {}",
                        attempts, e.getMessage());
                if (attempts < telegramConfig.getRetryAttempts()) {
                    try {
                        Thread.sleep(1000L * attempts);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    @Override
    public String getBotUsername() {
        return "TradingEngineBot";
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getBotToken() != null ? telegramConfig.getBotToken() : "";
    }

    @Override
    public void onUpdateReceived(final Update update) {
    }
}
