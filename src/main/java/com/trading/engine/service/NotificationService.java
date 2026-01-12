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
        sb.append(emoji).append(" **INTRADAY TRADE SIGNAL**\n\n");

        // Basic info
        sb.append("**Symbol:** ").append(signal.getSymbol()).append("\n");
        sb.append("**Direction:** ").append(signal.getDirection()).append("\n");
        sb.append("**Event:** ").append(signal.getEvent()).append("\n");

        // Market context (null-safe)
        if (signal.getMarketContext() != null) {
            sb.append("**Session:** ").append(signal.getMarketContext().getSession()).append("\n");
            sb.append("**Price:** ").append(signal.getMarketContext().getCurrentPrice()).append("\n\n");
        } else {
            sb.append("**Session:** N/A\n");
            sb.append("**Price:** N/A\n\n");
        }

        // Intraday Context
        if (signal.getIntradayContext() != null) {
            sb.append("**Intraday Analysis:**\n");
            sb.append("Context: ").append(signal.getIntradayContext().getContextSummary()).append("\n");
            sb.append("Trend 15m/5m/1m: ")
                    .append(signal.getIntradayContext().getTrendBias15m()).append("/")
                    .append(signal.getIntradayContext().getTrendBias5m()).append("/")
                    .append(signal.getIntradayContext().getTrendBias1m()).append("\n");
            sb.append("OI+Price: ").append(signal.getIntradayContext().getOiPriceBehavior()).append("\n");
            sb.append("Volume: ").append(signal.getIntradayContext().getVolumeConfirmation()).append("\n");
            sb.append("Confidence: ").append(signal.getIntradayContext().getConfidenceScore()).append("/100\n\n");
        }

        // AI Assessment (null-safe)
        if (signal.getAiAssessment() != null) {
            sb.append("**AI Assessment:**\n");
            sb.append("Quality: ").append(signal.getAiAssessment().getSetupQuality()).append("\n");
            sb.append("Alignment: ").append(signal.getAiAssessment().getAlignmentScore()).append("/100\n");
            sb.append("Key Risk: ").append(
                    signal.getAiAssessment().getRiskFactors() != null && !signal.getAiAssessment().getRiskFactors().isEmpty()
                            ? signal.getAiAssessment().getRiskFactors().get(0)
                            : "None identified"
            ).append("\n\n");
        }

        // Execution Plan
        if (signal.getExecutionPlan() != null) {
            final var plan = signal.getExecutionPlan();
            sb.append("**Execution Plan:**\n");
            sb.append("Model: ").append(plan.getExecutionModel()).append("\n");

            if (plan.getEntryZoneLow() != null && plan.getEntryZoneHigh() != null) {
                sb.append("Entry Zone: ").append(plan.getEntryZoneLow())
                        .append(" - ").append(plan.getEntryZoneHigh()).append("\n");
            }

            if (plan.getStopLogic() != null) {
                sb.append("Stop: ").append(plan.getStopLogic());
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
                            .append(target.getLogic()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Execution Checklist
        if (signal.getExecutionChecklist() != null) {
            final var checklist = signal.getExecutionChecklist();
            sb.append("**Pre-Execution Checklist:**\n");
            sb.append(checklist.getReadyForExecution() ? "✅ " : "❌ ")
                    .append(checklist.getChecklistSummary()).append("\n");

            if (checklist.getWarnings() != null && !checklist.getWarnings().isEmpty()) {
                sb.append("Warnings:\n");
                for (final var warning : checklist.getWarnings()) {
                    sb.append("  ⚠️ ").append(warning).append("\n");
                }
            }

            if (checklist.getBlockers() != null && !checklist.getBlockers().isEmpty()) {
                sb.append("Blockers:\n");
                for (final var blocker : checklist.getBlockers()) {
                    sb.append("  🚫 ").append(blocker).append("\n");
                }
            }
            sb.append("\n");
        }

        // Action
        sb.append("**Action:** ").append(signal.getAction()).append("\n\n");

        // Invalidation
        if (signal.getExecutionPlan() != null && signal.getExecutionPlan().getInvalidationConditions() != null) {
            sb.append("**Invalidation:**\n");
            for (final var condition : signal.getExecutionPlan().getInvalidationConditions()) {
                sb.append("• ").append(condition).append("\n");
            }
            sb.append("\n");
        }

        // AI Summary (null-safe)
        if (signal.getAiAssessment() != null && signal.getAiAssessment().getSummary() != null) {
            sb.append("**Summary:** ").append(signal.getAiAssessment().getSummary()).append("\n");
        }

        return sb.toString();
    }

    private void sendMessage(final String text) {
        final var message = new SendMessage();
        message.setChatId(telegramConfig.getChatId());
        message.setText(text);
        message.setParseMode("Markdown");

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
