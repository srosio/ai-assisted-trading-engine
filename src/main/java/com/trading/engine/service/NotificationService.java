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

    private String formatSignalMessage(final TradeSignal signal) {
        final var sb = new StringBuilder();

        final var emoji = signal.getStatus().equals("VALID") ? "✅" : "❌";
        sb.append(emoji).append(" <b>INTRADAY TRADE SIGNAL</b>\n\n");

        // Basic info
        sb.append("<b>Symbol:</b> ").append(escapeHtml(signal.getSymbol())).append("\n");
        sb.append("<b>Direction:</b> ").append(escapeHtml(signal.getDirection())).append("\n");
        sb.append("<b>Event:</b> ").append(escapeHtml(signal.getEvent())).append("\n");

        // Market context (null-safe)
        if (signal.getMarketContext() != null) {
            sb.append("<b>Session:</b> ").append(escapeHtml(signal.getMarketContext().getSession())).append("\n");
            sb.append("<b>Price:</b> ").append(signal.getMarketContext().getCurrentPrice()).append("\n\n");
        } else {
            sb.append("<b>Session:</b> N/A\n");
            sb.append("<b>Price:</b> N/A\n\n");
        }

        // Intraday Context
        if (signal.getIntradayContext() != null) {
            sb.append("<b>Intraday Analysis:</b>\n");
            sb.append("Context: ").append(escapeHtml(signal.getIntradayContext().getContextSummary())).append("\n");
            sb.append("Trend 15m/5m/1m: ")
                    .append(escapeHtml(signal.getIntradayContext().getTrendBias15m())).append("/")
                    .append(escapeHtml(signal.getIntradayContext().getTrendBias5m())).append("/")
                    .append(escapeHtml(signal.getIntradayContext().getTrendBias1m())).append("\n");
            sb.append("OI+Price: ").append(escapeHtml(signal.getIntradayContext().getOiPriceBehavior())).append("\n");
            sb.append("Volume: ").append(escapeHtml(signal.getIntradayContext().getVolumeConfirmation())).append("\n");
            sb.append("Confidence: ").append(signal.getIntradayContext().getConfidenceScore()).append("/100\n\n");
        }

        // AI Assessment (null-safe)
        if (signal.getAiAssessment() != null) {
            sb.append("<b>AI Assessment:</b>\n");
            sb.append("Quality: ").append(escapeHtml(signal.getAiAssessment().getSetupQuality())).append("\n");
            sb.append("Alignment: ").append(signal.getAiAssessment().getAlignmentScore()).append("/100\n");
            sb.append("Key Risk: ").append(escapeHtml(
                    signal.getAiAssessment().getRiskFactors() != null && !signal.getAiAssessment().getRiskFactors().isEmpty()
                            ? signal.getAiAssessment().getRiskFactors().get(0)
                            : "None identified"
            )).append("\n\n");
        }

        // Execution Plan
        if (signal.getExecutionPlan() != null) {
            final var plan = signal.getExecutionPlan();
            sb.append("<b>Execution Plan:</b>\n");
            sb.append("Model: ").append(escapeHtml(plan.getExecutionModel())).append("\n");

            if (plan.getEntryZoneLow() != null && plan.getEntryZoneHigh() != null) {
                sb.append("Entry Zone: ").append(plan.getEntryZoneLow())
                        .append(" - ").append(plan.getEntryZoneHigh()).append("\n");
            }

            if (plan.getStopLogic() != null) {
                sb.append("Stop: ").append(escapeHtml(plan.getStopLogic()));
                if (plan.getSuggestedStopPrice() != null) {
                    sb.append(" @ ").append(plan.getSuggestedStopPrice());
                }
                sb.append("\n");
            }

            if (plan.getTargets() != null && !plan.getTargets().isEmpty()) {
                sb.append("Targets:\n");
                for (final var target : plan.getTargets()) {
                    sb.append("  - ").append(target.getPrice())
                            .append(" (").append(target.getRMultiple()).append("R): ")
                            .append(escapeHtml(target.getLogic())).append("\n");
                }
            }
            sb.append("\n");
        }

        // Execution Checklist
        if (signal.getExecutionChecklist() != null) {
            final var checklist = signal.getExecutionChecklist();
            sb.append("<b>Pre-Execution Checklist:</b>\n");
            sb.append(checklist.getReadyForExecution() ? "✅ " : "❌ ")
                    .append(escapeHtml(checklist.getChecklistSummary())).append("\n");

            if (checklist.getWarnings() != null && !checklist.getWarnings().isEmpty()) {
                sb.append("Warnings:\n");
                for (final var warning : checklist.getWarnings()) {
                    sb.append("  ⚠️ ").append(escapeHtml(warning)).append("\n");
                }
            }

            if (checklist.getBlockers() != null && !checklist.getBlockers().isEmpty()) {
                sb.append("Blockers:\n");
                for (final var blocker : checklist.getBlockers()) {
                    sb.append("  🚫 ").append(escapeHtml(blocker)).append("\n");
                }
            }
            sb.append("\n");
        }

        // Action
        sb.append("<b>Action:</b> ").append(escapeHtml(signal.getAction())).append("\n\n");

        // Invalidation
        if (signal.getExecutionPlan() != null && signal.getExecutionPlan().getInvalidationConditions() != null) {
            sb.append("<b>Invalidation:</b>\n");
            for (final var condition : signal.getExecutionPlan().getInvalidationConditions()) {
                sb.append("• ").append(escapeHtml(condition)).append("\n");
            }
            sb.append("\n");
        }

        // AI Summary (null-safe)
        if (signal.getAiAssessment() != null && signal.getAiAssessment().getSummary() != null) {
            sb.append("<b>Summary:</b> ").append(escapeHtml(signal.getAiAssessment().getSummary())).append("\n");
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
