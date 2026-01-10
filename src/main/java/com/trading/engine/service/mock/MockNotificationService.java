package com.trading.engine.service.mock;

import com.trading.engine.domain.TradeSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Mock Notification Service
 * Logs notifications instead of sending to Telegram when bot token is not configured.
 *
 * Useful for:
 * - Testing without Telegram setup
 * - Development environment
 * - CI/CD pipelines
 */
@Service
@Primary
@ConditionalOnProperty(name = "mock.enabled", havingValue = "true")
@Slf4j
public class MockNotificationService extends TelegramLongPollingBot {

    public void sendSignalNotification(TradeSignal signal) {
        log.info("[MOCK TELEGRAM] ========================================");
        log.info("[MOCK TELEGRAM] TRADE SIGNAL NOTIFICATION");
        log.info("[MOCK TELEGRAM] ========================================");
        log.info("[MOCK TELEGRAM] Symbol: {}", signal.getSymbol());
        log.info("[MOCK TELEGRAM] Direction: {}", signal.getDirection());
        log.info("[MOCK TELEGRAM] Event: {}", signal.getEvent());
        log.info("[MOCK TELEGRAM] Session: {}", signal.getMarketContext().getSession());
        log.info("[MOCK TELEGRAM] AI Quality: {}", signal.getAiAssessment().getSetupQuality());
        log.info("[MOCK TELEGRAM] Alignment: {}/100", signal.getAiAssessment().getAlignmentScore());
        log.info("[MOCK TELEGRAM] Rule Check: {}", signal.getRuleResult().isPassed() ? "PASS" : "FAIL");
        log.info("[MOCK TELEGRAM] Status: {}", signal.getStatus());
        log.info("[MOCK TELEGRAM] Action: {}", signal.getAction());

        if (signal.getRiskCalculation() != null) {
            log.info("[MOCK TELEGRAM] Entry: {}", signal.getRiskCalculation().getEntryPrice());
            log.info("[MOCK TELEGRAM] Stop: {}", signal.getRiskCalculation().getStopLoss());
            log.info("[MOCK TELEGRAM] Target: {}", signal.getRiskCalculation().getTakeProfit());
            log.info("[MOCK TELEGRAM] R:R: 1:{}", signal.getRiskCalculation().getRiskRewardRatio());
        }

        log.info("[MOCK TELEGRAM] ========================================");
    }

    public void sendAlert(String message) {
        log.info("[MOCK TELEGRAM] Alert: {}", message);
    }

    @Override
    public String getBotUsername() {
        return "MockTradingEngineBot";
    }

    @Override
    public String getBotToken() {
        return "mock-token";
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Mock implementation - does nothing
    }
}
