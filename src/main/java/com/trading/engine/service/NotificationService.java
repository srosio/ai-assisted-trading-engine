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

        // Header - Clear status first
        if (isValid) {
            sb.append("✅ <b>TRADE SIGNAL</b>\n\n");
        } else {
            sb.append("❌ <b>TRADE BLOCKED</b>\n\n");
        }

        // Trade basics - concise, one line
        sb.append("<b>").append(escapeHtml(signal.getSymbol())).append(" ")
                .append(escapeHtml(signal.getDirection()));
        if (signal.getMarketContext() != null) {
            sb.append(" @ ").append(signal.getMarketContext().getCurrentPrice());
        }
        sb.append("</b>\n");

        sb.append("Event: ").append(escapeHtml(signal.getEvent()));
        if (signal.getMarketContext() != null) {
            sb.append(" | Session: ").append(escapeHtml(signal.getMarketContext().getSession()));
        }
        sb.append("\n\n");

        // For blocked signals, show why immediately
        if (!isValid) {
            sb.append("<b>⚠️ Why Blocked:</b>\n");
            if (signal.getAiAssessment() != null && signal.getAiAssessment().getRiskFactors() != null
                    && !signal.getAiAssessment().getRiskFactors().isEmpty()) {
                sb.append(escapeHtml(signal.getAiAssessment().getRiskFactors().get(0))).append("\n\n");
            } else {
                sb.append(escapeHtml(signal.getAction())).append("\n\n");
            }
        }

        // Quality metrics - single line
        sb.append("<b>Quality:</b> ");
        if (signal.getAiAssessment() != null) {
            sb.append(escapeHtml(signal.getAiAssessment().getSetupQuality()))
                    .append(" | Alignment: ").append(signal.getAiAssessment().getAlignmentScore()).append("/100");
        }
        if (signal.getIntradayContext() != null) {
            sb.append(" | Confidence: ").append(signal.getIntradayContext().getConfidenceScore()).append("/100");
        }
        sb.append("\n\n");

        // For valid signals, show execution plan
        if (isValid && signal.getExecutionPlan() != null) {
            final var plan = signal.getExecutionPlan();
            sb.append("<b>Execution:</b>\n");

            if (plan.getEntryZoneLow() != null && plan.getEntryZoneHigh() != null) {
                sb.append("Entry: ").append(plan.getEntryZoneLow())
                        .append("-").append(plan.getEntryZoneHigh()).append("\n");
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

        // Warnings/blockers - consolidated
        if (signal.getExecutionChecklist() != null) {
            final var checklist = signal.getExecutionChecklist();
            final var hasWarnings = checklist.getWarnings() != null && !checklist.getWarnings().isEmpty();
            final var hasBlockers = checklist.getBlockers() != null && !checklist.getBlockers().isEmpty();

            if (hasWarnings || hasBlockers) {
                sb.append("<b>Issues:</b>\n");
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

        // Summary - key insight only
        if (signal.getAiAssessment() != null && signal.getAiAssessment().getSummary() != null) {
            final var summary = signal.getAiAssessment().getSummary();
            // Extract first sentence or up to 200 chars
            final var shortSummary = summary.length() > 200
                    ? summary.substring(0, summary.indexOf('.', 0) + 1)
                    : summary;
            sb.append("<b>Note:</b> ").append(escapeHtml(shortSummary)).append("\n");
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
