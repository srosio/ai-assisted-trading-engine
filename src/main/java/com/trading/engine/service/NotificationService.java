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
        sb.append(emoji).append(" **TRADE SIGNAL**\n\n");

        // Basic info
        sb.append("**Symbol:** ").append(signal.getSymbol()).append("\n");
        sb.append("**Direction:** ").append(signal.getDirection()).append("\n");
        sb.append("**Event:** ").append(signal.getEvent()).append("\n");
        sb.append("**Session:** ").append(signal.getMarketContext().getSession()).append("\n");
        sb.append("**Price:** ").append(signal.getMarketContext().getCurrentPrice()).append("\n\n");

        // AI Assessment
        sb.append("**AI Quality:** ").append(signal.getAiAssessment().getSetupQuality()).append("\n");
        sb.append("**Alignment:** ").append(signal.getAiAssessment().getAlignmentScore()).append("/100\n");
        sb.append("**Key Risk:** ").append(
                signal.getAiAssessment().getRiskFactors().isEmpty()
                        ? "None identified"
                        : signal.getAiAssessment().getRiskFactors().get(0)
        ).append("\n\n");

        // Market Context
        sb.append("**Market Data:**\n");
        sb.append("HTF Bias: ").append(signal.getMarketContext().getHtfBias()).append("\n");
        sb.append("Volatility: ").append(signal.getMarketContext().getVolatility()).append("\n");
        if (signal.getMarketContext().getOiChangePercent() != null) {
            sb.append("OI Change: ").append(String.format("%.2f%%", signal.getMarketContext().getOiChangePercent())).append("\n");
        }
        sb.append("\n");

        // Rule Check
        sb.append("**Rule Check:** ");
        if (signal.getRuleResult().isPassed()) {
            sb.append("✅ PASS\n");
        } else {
            sb.append("❌ FAIL\n");
            sb.append("**Blocked by:** ").append(signal.getRuleResult().getFailedRules().get(0)).append("\n");
        }
        sb.append("\n");

        // Action
        sb.append("**Action:** ").append(signal.getAction()).append("\n\n");

        // Invalidation
        sb.append("**Invalidation:** ").append(signal.getAiAssessment().getInvalidation()).append("\n\n");

        // AI Summary
        sb.append("**Summary:** ").append(signal.getAiAssessment().getSummary()).append("\n");

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
                        Thread.sleep(1000 * attempts);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    public void sendAlert(final String message) {
        if (!telegramConfig.getEnabled()) {
            return;
        }
        sendMessage(message);
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
