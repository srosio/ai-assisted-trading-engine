package com.trading.engine.service;

import com.trading.engine.config.TelegramConfig;
import com.trading.engine.domain.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
        final var sb = new StringBuilder();
        final var isValid = signal.getStatus().equals("VALID");

        // Header with status
        final var emoji = isValid ? "✅" : "⚠️";
        sb.append(emoji).append(" <b>TRADE SIGNAL</b>\n\n");

        // Signal basics
        sb.append("<b>").append(escapeHtml(signal.getSymbol())).append(" ")
                .append(escapeHtml(signal.getDirection()));
        if (signal.getMarketContext() != null) {
            sb.append(" @ ").append(signal.getMarketContext().getCurrentPrice());
        }
        sb.append("</b>\n");

        // Display strategy and event type
        if (signal.getStrategy() != null) {
            sb.append(escapeHtml(signal.getStrategy())).append(" - ").append(escapeHtml(signal.getEventType()));
        } else if (signal.getEvent() != null) {
            sb.append(escapeHtml(signal.getEvent()));
        }
        if (signal.getMarketContext() != null) {
            sb.append(" | ").append(escapeHtml(signal.getMarketContext().getSession()));
        }
        sb.append("\n\n");

        // Market Analysis
        if (signal.getIntradayContext() != null) {
            final var ctx = signal.getIntradayContext();
            sb.append("<b>📊 Market Analysis:</b>\n");
            sb.append("• Trends (15m/5m/1m): ")
                    .append(escapeHtml(ctx.getTrendBias15m())).append("/")
                    .append(escapeHtml(ctx.getTrendBias5m())).append("/")
                    .append(escapeHtml(ctx.getTrendBias1m())).append("\n");
            sb.append("• Volume: ").append(escapeHtml(ctx.getVolumeConfirmation())).append("\n");
            sb.append("• OI+Price: ").append(escapeHtml(ctx.getOiPriceBehavior())).append("\n");
            sb.append("• Context: ").append(escapeHtml(ctx.getContextSummary())).append("\n");
            sb.append("• Confidence: ").append(ctx.getConfidenceScore()).append("/100\n\n");
        }

        // AI Analysis section
        if (signal.getAiAssessment() != null) {
            final var ai = signal.getAiAssessment();
            sb.append("<b>🤖 AI Analysis:</b>\n");
            sb.append("• Setup Quality: ").append(escapeHtml(ai.getSetupQuality())).append("\n");
            sb.append("• HTF Alignment: ").append(ai.getAlignmentScore()).append("/100\n");

            if (ai.getRiskFactors() != null && !ai.getRiskFactors().isEmpty()) {
                sb.append("• Key Risk: ").append(escapeHtml(ai.getRiskFactors().get(0))).append("\n");
            }

            if (ai.getSummary() != null) {
                // Get first sentence of summary
                final var summary = ai.getSummary();
                final var dotIndex = summary.indexOf('.');
                final var firstSentence = dotIndex > 0 && dotIndex < 200
                    ? summary.substring(0, dotIndex + 1)
                    : (summary.length() > 200 ? summary.substring(0, 200) + "..." : summary);
                sb.append("• Assessment: ").append(escapeHtml(firstSentence)).append("\n");
            }
            sb.append("\n");
        }

        // Execution Plan
        if (signal.getExecutionPlan() != null) {
            final var plan = signal.getExecutionPlan();
            sb.append("<b>📍 Execution Plan:</b>\n");

            if (plan.getEntryZoneLow() != null && plan.getEntryZoneHigh() != null) {
                sb.append("Entry: ").append(plan.getEntryZoneLow())
                        .append(" - ").append(plan.getEntryZoneHigh()).append("\n");
            }

            if (plan.getSuggestedStopPrice() != null) {
                sb.append("Stop: ").append(plan.getSuggestedStopPrice()).append("\n");
            }

            if (plan.getTargets() != null && !plan.getTargets().isEmpty()) {
                sb.append("Targets: ");
                for (int i = 0; i < Math.min(plan.getTargets().size(), 3); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(plan.getTargets().get(i).getPrice());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Warnings and Issues
        if (signal.getExecutionChecklist() != null) {
            final var checklist = signal.getExecutionChecklist();
            final var hasWarnings = checklist.getWarnings() != null && !checklist.getWarnings().isEmpty();
            final var hasBlockers = checklist.getBlockers() != null && !checklist.getBlockers().isEmpty();

            if (hasWarnings || hasBlockers) {
                sb.append("<b>⚠️ Review Points:</b>\n");
                if (hasBlockers) {
                    for (final var blocker : checklist.getBlockers()) {
                        sb.append("🚫 ").append(escapeHtml(blocker)).append("\n");
                    }
                }
                if (hasWarnings) {
                    for (final var warning : checklist.getWarnings()) {
                        sb.append("⚠️ ").append(escapeHtml(warning)).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // Final recommendation
        sb.append("<b>Recommendation:</b> ");
        if (isValid) {
            sb.append("✅ Setup meets criteria - ready for execution\n");
        } else {
            sb.append("⚠️ Review carefully - ");
            sb.append(escapeHtml(signal.getAction())).append("\n");
        }

        return sb.toString();
    }

    /**
     * Escape HTML special characters to prevent formatting issues
     */
    private String escapeHtml(final String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
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
