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

        log.info("Received TradingView webhook - Symbol: {}, Strategy: {}, Event: {}, Direction: {}, Session: {}",
                webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType(), webhook.getDirection(), webhook.getSession());

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
        webhook.setSession(alignedSession);

        // Step 4: Process webhook asynchronously (fire and forget)
        signalProcessor.processWebhookAsync(webhook);

        // Step 5: Respond immediately with HTTP 202 Accepted
        final var response = Map.of(
                "success", true,
                "status", "ACCEPTED",
                "message", "Webhook received and processing started",
                "symbol", webhook.getSymbol(),
                "strategy", webhook.getStrategy(),
                "event_type", webhook.getEventType()
        );

        log.info("Webhook accepted for async processing - Symbol: {}, Strategy: {}, Event: {}",
                webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
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

        log.info("Test signal received - Symbol: {}, Strategy: {}, Event: {}",
                webhook.getSymbol(), webhook.getStrategy(), webhook.getEventType());

        final var signal = signalProcessor.processWebhook(webhook);

        final var response = Map.of("signal", signal);

        return ResponseEntity.ok(response);
    }
}
