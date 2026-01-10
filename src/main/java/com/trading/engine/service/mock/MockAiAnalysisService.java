package com.trading.engine.service.mock;

import com.trading.engine.domain.AiAssessment;
import com.trading.engine.domain.MarketContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
@Primary
@ConditionalOnProperty(name = "mock.enabled", havingValue = "true")
@Slf4j
public class MockAiAnalysisService {

    private final Random random = new Random();

    public AiAssessment analyzeContext(final MarketContext context) {
        log.info("[MOCK] Analyzing market context for {} - Event: {}",
                context.getSymbol(), context.getLiquidityEvent());

        try {
            Thread.sleep(500 + random.nextInt(1000));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final var quality = generateQuality(context);
        final var risks = generateRiskFactors(context);
        final var invalidation = generateInvalidation(context);
        final var alignmentScore = generateAlignmentScore(quality);

        final var assessment = AiAssessment.builder()
                .setupQuality(quality)
                .riskFactors(risks)
                .invalidation(invalidation)
                .summary(generateSummary(context, quality))
                .alignmentScore(alignmentScore)
                .keyObservation(generateKeyObservation(context))
                .build();

        log.info("[MOCK] AI Assessment complete - Quality: {}, Alignment: {}",
                quality, alignmentScore);

        return assessment;
    }

    private String generateQuality(final MarketContext context) {
        var score = 0;

        if (context.getHtfBias() != null && !context.getHtfBias().equals("neutral")) {
            score += 30;
        }

        if (Boolean.TRUE.equals(context.getVolumeSpike())) {
            score += 25;
        }

        if (context.getOiChangePercent() != null && context.getOiChangePercent() > 2.0) {
            score += 20;
        }

        if (Boolean.TRUE.equals(context.getDisplacementDetected())) {
            score += 25;
        }

        if (score >= 70) return "A";
        if (score >= 40) return "B";
        return "C";
    }

    private List<String> generateRiskFactors(final MarketContext context) {
        final var allRisks = Arrays.asList(
                "HTF resistance nearby",
                "Funding rate skewed",
                "Low volume session ahead",
                "Previous rejection zone",
                "Widening spreads",
                "OI divergence",
                "Key level retest needed"
        );

        final var numRisks = 1 + random.nextInt(3);
        return allRisks.subList(0, numRisks);
    }

    private String generateInvalidation(final MarketContext context) {
        final var event = context.getLiquidityEvent();

        if (event != null && event.toLowerCase().contains("long")) {
            return "Break below swept low";
        } else if (event != null && event.toLowerCase().contains("short")) {
            return "Break above swept high";
        }

        return "Loss of structural integrity";
    }

    private int generateAlignmentScore(final String quality) {
        return switch (quality) {
            case "A" -> 75 + random.nextInt(20);
            case "B" -> 50 + random.nextInt(25);
            case "C" -> 25 + random.nextInt(25);
            default -> 50;
        };
    }

    private String generateSummary(final MarketContext context, final String quality) {
        if (quality.equals("A")) {
            return "Strong alignment with liquidity sweep model. Conditions favor continuation if volume sustains.";
        } else if (quality.equals("B")) {
            return "Moderate setup with some confirmation. Consider reduced position size.";
        } else {
            return "Weak setup lacking key confirmations. Not recommended for execution.";
        }
    }

    private String generateKeyObservation(final MarketContext context) {
        final var observations = Arrays.asList(
                "Watch for follow-through on next candle",
                "Monitor funding rate for reversal",
                "HTF bias confirms direction",
                "Volume profile shows strength",
                "Key level holding as support",
                "Session volatility within normal range"
        );

        return observations.get(random.nextInt(observations.size()));
    }

    public boolean isHealthy() {
        log.debug("[MOCK] AI service health check (mock mode)");
        return true;
    }
}
