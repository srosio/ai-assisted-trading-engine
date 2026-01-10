package com.trading.engine.controller;

import com.trading.engine.domain.TradingViewWebhook;
import com.trading.engine.service.IngressService;
import com.trading.engine.service.SignalProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final IngressService ingressService;
    private final SignalProcessingService signalProcessor;

    @PostMapping("/tradingview")
    public ResponseEntity<?> receiveTradingViewWebhook(
            @Valid @RequestBody final TradingViewWebhook webhook) {

        log.info("Received TradingView webhook - Symbol: {}, Event: {}, Session: {}",
                webhook.getSymbol(), webhook.getEvent(), webhook.getSession());

        try {
            // Step 1: Ingress Layer - Validate payload
            if (!ingressService.isValidPayload(webhook)) {
                final var errorResponse = Map.of(
                        "success", false,
                        "error", "Invalid payload: missing required fields"
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }

            // Step 2: Event deduplication
            if (ingressService.isDuplicate(webhook)) {
                log.info("Duplicate event ignored - skipping processing");
                final var response = Map.of(
                        "success", true,
                        "status", "DUPLICATE",
                        "message", "Event already processed within deduplication window"
                );
                return ResponseEntity.ok(response);
            }

            // Step 3: Time alignment and session tagging
            final var alignedSession = ingressService.processIngress(webhook);
            webhook.setSession(alignedSession); // Override with exchange-aligned session

            // Step 4: Process webhook through pipeline
            final var signal = signalProcessor.processWebhook(webhook);

            // Build response
            final var response = Map.of(
                    "success", true,
                    "signalId", signal.getSignalId(),
                    "status", signal.getStatus(),
                    "action", signal.getAction(),
                    "setupQuality", signal.getAiAssessment() != null
                            ? signal.getAiAssessment().getSetupQuality() : "N/A",
                    "rulesPassed", signal.getRuleResult() != null
                                   && signal.getRuleResult().isPassed()
            );

            log.info("Webhook processed successfully - Signal ID: {}, Status: {}",
                    signal.getSignalId(), signal.getStatus());

            return ResponseEntity.ok(response);

        } catch (final Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);

            final var errorResponse = Map.of(
                    "success", false,
                    "error", e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        final var status = Map.of(
                "status", "UP",
                "service", "AI-Assisted Trading Engine"
        );
        return ResponseEntity.ok(status);
    }

    @PostMapping("/test")
    public ResponseEntity<?> testSignal(
            @RequestBody final TradingViewWebhook webhook) {

        log.info("Test signal received - Symbol: {}, Event: {}",
                webhook.getSymbol(), webhook.getEvent());

        final var signal = signalProcessor.processWebhook(webhook);

        final var response = Map.of("signal", signal);

        return ResponseEntity.ok(response);
    }
}
