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

/**
 * Mock AI Analysis Service
 * Returns simulated AI assessments when Claude API key is not configured.
 *
 * Useful for:
 * - Testing without Claude API access
 * - Development environment
 * - Cost-free testing
 */
@Service
@Primary
@ConditionalOnProperty(name = "mock.enabled", havingValue = "true")
@Slf4j
public class MockAiAnalysisService {

    private final Random random = new Random();

    public AiAssessment analyzeContext(MarketContext context) {
        log.info("[MOCK] Analyzing market context for {} - Event: {}",
                context.getSymbol(), context.getLiquidityEvent());

        // Simulate processing time
        try {
            Thread.sleep(500 + random.nextInt(1000)); // 0.5-1.5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate realistic mock assessment
        String quality = generateQuality(context);
        List<String> risks = generateRiskFactors(context);
        String invalidation = generateInvalidation(context);
        int alignmentScore = generateAlignmentScore(quality);

        AiAssessment assessment = AiAssessment.builder()
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

    private String generateQuality(MarketContext context) {
        // Assess quality based on context factors
        int score = 0;

        // HTF alignment adds points
        if (context.getHtfBias() != null && !context.getHtfBias().equals("neutral")) {
            score += 30;
        }

        // Volume spike is positive
        if (Boolean.TRUE.equals(context.getVolumeSpike())) {
            score += 25;
        }

        // OI increase is positive
        if (context.getOiChangePercent() != null && context.getOiChangePercent() > 2.0) {
            score += 20;
        }

        // Displacement is positive
        if (Boolean.TRUE.equals(context.getDisplacementDetected())) {
            score += 25;
        }

        // Map score to quality
        if (score >= 70) return "A";
        if (score >= 40) return "B";
        return "C";
    }

    private List<String> generateRiskFactors(MarketContext context) {
        List<String> allRisks = Arrays.asList(
                "HTF resistance nearby",
                "Funding rate skewed",
                "Low volume session ahead",
                "Previous rejection zone",
                "Widening spreads",
                "OI divergence",
                "Key level retest needed"
        );

        // Return 1-3 random risks
        int numRisks = 1 + random.nextInt(3);
        return allRisks.subList(0, numRisks);
    }

    private String generateInvalidation(MarketContext context) {
        String event = context.getLiquidityEvent();

        if (event != null && event.toLowerCase().contains("long")) {
            return "Break below swept low";
        } else if (event != null && event.toLowerCase().contains("short")) {
            return "Break above swept high";
        }

        return "Loss of structural integrity";
    }

    private int generateAlignmentScore(String quality) {
        return switch (quality) {
            case "A" -> 75 + random.nextInt(20); // 75-95
            case "B" -> 50 + random.nextInt(25); // 50-75
            case "C" -> 25 + random.nextInt(25); // 25-50
            default -> 50;
        };
    }

    private String generateSummary(MarketContext context, String quality) {
        if (quality.equals("A")) {
            return "Strong alignment with liquidity sweep model. Conditions favor continuation if volume sustains.";
        } else if (quality.equals("B")) {
            return "Moderate setup with some confirmation. Consider reduced position size.";
        } else {
            return "Weak setup lacking key confirmations. Not recommended for execution.";
        }
    }

    private String generateKeyObservation(MarketContext context) {
        List<String> observations = Arrays.asList(
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
