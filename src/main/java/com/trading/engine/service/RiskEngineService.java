package com.trading.engine.service;

import com.trading.engine.config.TradingConfig;
import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.RiskCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Risk Engine with NON-NEGOTIABLE risk management.
 * AI CANNOT modify these parameters.
 *
 * Rules:
 * - Risk per trade: 0.5% - 1%
 * - Minimum R:R: 1:3
 * - Max trades per day: 2
 * - Max daily loss: -2R
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final TradingConfig tradingConfig;
    private final JournalService journalService;

    /**
     * Calculate risk parameters and position size
     */
    public RiskCalculation calculateRisk(MarketContext context, String direction) {
        log.info("Calculating risk for {} - Direction: {}", context.getSymbol(), direction);

        BigDecimal accountBalance = tradingConfig.getAccountBalance();
        BigDecimal riskPercent = tradingConfig.getDefaultRiskPercent();

        // Calculate risk amount in dollars
        BigDecimal riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // Determine entry, stop loss, and take profit based on direction and ATR
        BigDecimal entryPrice = context.getCurrentPrice();
        BigDecimal atr = context.getAtrValue();

        // Use ATR for stop loss calculation (1.5 ATR typical)
        BigDecimal stopDistance = atr.multiply(new BigDecimal("1.5"));

        BigDecimal stopLoss;
        BigDecimal takeProfit;

        if (direction.equalsIgnoreCase("LONG")) {
            stopLoss = entryPrice.subtract(stopDistance);
            takeProfit = entryPrice.add(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        } else {
            stopLoss = entryPrice.add(stopDistance);
            takeProfit = entryPrice.subtract(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        }

        // Calculate position size
        BigDecimal positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        // Calculate R:R ratio
        BigDecimal reward = takeProfit.subtract(entryPrice).abs();
        BigDecimal risk = entryPrice.subtract(stopLoss).abs();
        BigDecimal rrRatio = reward.divide(risk, 2, RoundingMode.HALF_UP);

        // Get today's stats
        long tradesUsedToday = journalService.getTodayTradeCount();
        BigDecimal dailyLoss = journalService.getTodayPnL();

        // Check limits
        boolean withinLimits = true;
        String limitViolation = null;

        if (tradesUsedToday >= tradingConfig.getMaxTradesPerDay()) {
            withinLimits = false;
            limitViolation = "Max trades per day reached";
        }

        if (dailyLoss.compareTo(tradingConfig.getMaxDailyLossR().negate().multiply(riskAmount)) < 0) {
            withinLimits = false;
            limitViolation = "Daily loss limit exceeded";
        }

        if (rrRatio.compareTo(tradingConfig.getMinRiskRewardRatio()) < 0) {
            withinLimits = false;
            limitViolation = "R:R ratio below minimum of " + tradingConfig.getMinRiskRewardRatio();
        }

        RiskCalculation calculation = RiskCalculation.builder()
                .accountBalance(accountBalance)
                .riskPercentage(riskPercent)
                .riskAmount(riskAmount)
                .entryPrice(entryPrice)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .positionSize(positionSize)
                .riskRewardRatio(rrRatio)
                .tradesUsedToday(Math.toIntExact(tradesUsedToday))
                .maxTradesPerDay(tradingConfig.getMaxTradesPerDay())
                .dailyLoss(dailyLoss)
                .maxDailyLoss(tradingConfig.getMaxDailyLossR().multiply(riskAmount))
                .withinRiskLimits(withinLimits)
                .limitViolation(limitViolation)
                .build();

        log.info("Risk calculation complete - Position size: {}, R:R: {}, Within limits: {}",
                positionSize, rrRatio, withinLimits);

        return calculation;
    }

    /**
     * Calculate custom risk with specific stop loss
     * (for when trader has a specific stop in mind)
     */
    public RiskCalculation calculateRiskWithStop(BigDecimal entryPrice, BigDecimal stopLoss,
                                                   String direction, String symbol) {
        BigDecimal accountBalance = tradingConfig.getAccountBalance();
        BigDecimal riskPercent = tradingConfig.getDefaultRiskPercent();
        BigDecimal riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal stopDistance = entryPrice.subtract(stopLoss).abs();
        BigDecimal positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        // Calculate take profit based on minimum R:R
        BigDecimal takeProfit;
        if (direction.equalsIgnoreCase("LONG")) {
            takeProfit = entryPrice.add(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        } else {
            takeProfit = entryPrice.subtract(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        }

        BigDecimal reward = takeProfit.subtract(entryPrice).abs();
        BigDecimal risk = entryPrice.subtract(stopLoss).abs();
        BigDecimal rrRatio = reward.divide(risk, 2, RoundingMode.HALF_UP);

        long tradesUsedToday = journalService.getTodayTradeCount();
        BigDecimal dailyLoss = journalService.getTodayPnL();

        boolean withinLimits = tradesUsedToday < tradingConfig.getMaxTradesPerDay()
                && dailyLoss.compareTo(tradingConfig.getMaxDailyLossR().negate().multiply(riskAmount)) >= 0
                && rrRatio.compareTo(tradingConfig.getMinRiskRewardRatio()) >= 0;

        return RiskCalculation.builder()
                .accountBalance(accountBalance)
                .riskPercentage(riskPercent)
                .riskAmount(riskAmount)
                .entryPrice(entryPrice)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .positionSize(positionSize)
                .riskRewardRatio(rrRatio)
                .tradesUsedToday(Math.toIntExact(tradesUsedToday))
                .maxTradesPerDay(tradingConfig.getMaxTradesPerDay())
                .dailyLoss(dailyLoss)
                .maxDailyLoss(tradingConfig.getMaxDailyLossR().multiply(riskAmount))
                .withinRiskLimits(withinLimits)
                .build();
    }

    /**
     * Validate that risk calculation is acceptable
     */
    public boolean isRiskAcceptable(RiskCalculation calculation) {
        if (!calculation.isWithinRiskLimits()) {
            log.warn("Risk calculation failed limits check: {}", calculation.getLimitViolation());
            return false;
        }

        if (calculation.getRiskRewardRatio().compareTo(tradingConfig.getMinRiskRewardRatio()) < 0) {
            log.warn("R:R ratio {} below minimum {}",
                    calculation.getRiskRewardRatio(), tradingConfig.getMinRiskRewardRatio());
            return false;
        }

        return true;
    }
}
