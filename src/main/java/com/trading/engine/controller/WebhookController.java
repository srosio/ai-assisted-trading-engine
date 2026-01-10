package com.trading.engine.controller;

import com.trading.engine.domain.TradeSignal;
import com.trading.engine.domain.TradingViewWebhook;
import com.trading.engine.service.SignalProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhook controller for receiving TradingView signals.
 * This is the entry point for all trading signals.
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final SignalProcessingService signalProcessor;

    /**
     * Receive TradingView webhook
     * POST /api/webhook/tradingview
     */
    @PostMapping("/tradingview")
    public ResponseEntity<Map<String, Object>> receiveTradingViewWebhook(
            @Valid @RequestBody TradingViewWebhook webhook) {

        log.info("Received TradingView webhook - Symbol: {}, Event: {}, Session: {}",
                webhook.getSymbol(), webhook.getEvent(), webhook.getSession());

        try {
            // Process webhook through pipeline
            TradeSignal signal = signalProcessor.processWebhook(webhook);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("signal_id", signal.getSignalId());
            response.put("status", signal.getStatus());
            response.put("action", signal.getAction());
            response.put("setup_quality", signal.getAiAssessment() != null
                    ? signal.getAiAssessment().getSetupQuality() : "N/A");
            response.put("rules_passed", signal.getRuleResult() != null
                    && signal.getRuleResult().isPassed());

            log.info("Webhook processed successfully - Signal ID: {}, Status: {}",
                    signal.getSignalId(), signal.getStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "AI-Assisted Trading Engine");
        return ResponseEntity.ok(status);
    }

    /**
     * Test endpoint for manual signal testing
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testSignal(
            @RequestBody TradingViewWebhook webhook) {

        log.info("Test signal received - Symbol: {}, Event: {}",
                webhook.getSymbol(), webhook.getEvent());

        TradeSignal signal = signalProcessor.processWebhook(webhook);

        Map<String, Object> response = new HashMap<>();
        response.put("signal", signal);

        return ResponseEntity.ok(response);
    }
}
