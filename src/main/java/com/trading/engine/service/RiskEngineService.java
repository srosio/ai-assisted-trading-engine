package com.trading.engine.service;

import com.trading.engine.config.TradingConfig;
import com.trading.engine.domain.MarketContext;
import com.trading.engine.domain.RiskCalculation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final TradingConfig tradingConfig;
    private final JournalService journalService;

    public RiskCalculation calculateRisk(final MarketContext context, final String direction) {
        log.info("Calculating risk for {} - Direction: {}", context.getSymbol(), direction);

        final var accountBalance = tradingConfig.getAccountBalance();
        final var riskPercent = tradingConfig.getDefaultRiskPercent();

        final var riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        final var entryPrice = context.getCurrentPrice();
        final var atr = context.getAtrValue();

        final var stopDistance = atr.multiply(new BigDecimal("1.5"));

        final BigDecimal stopLoss;
        final BigDecimal takeProfit;

        if (direction.equalsIgnoreCase("LONG")) {
            stopLoss = entryPrice.subtract(stopDistance);
            takeProfit = entryPrice.add(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        } else {
            stopLoss = entryPrice.add(stopDistance);
            takeProfit = entryPrice.subtract(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        }

        final var positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        final var reward = takeProfit.subtract(entryPrice).abs();
        final var risk = entryPrice.subtract(stopLoss).abs();
        final var rrRatio = reward.divide(risk, 2, RoundingMode.HALF_UP);

        final var tradesUsedToday = journalService.getTodayTradeCount();
        final var dailyLoss = journalService.getTodayPnL();

        var withinLimits = true;
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

        final var calculation = RiskCalculation.builder()
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

    public RiskCalculation calculateRiskWithStop(final BigDecimal entryPrice, final BigDecimal stopLoss,
                                                   final String direction, final String symbol) {
        final var accountBalance = tradingConfig.getAccountBalance();
        final var riskPercent = tradingConfig.getDefaultRiskPercent();
        final var riskAmount = accountBalance
                .multiply(riskPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        final var stopDistance = entryPrice.subtract(stopLoss).abs();
        final var positionSize = riskAmount.divide(stopDistance, 8, RoundingMode.HALF_UP);

        final BigDecimal takeProfit;
        if (direction.equalsIgnoreCase("LONG")) {
            takeProfit = entryPrice.add(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        } else {
            takeProfit = entryPrice.subtract(stopDistance.multiply(tradingConfig.getMinRiskRewardRatio()));
        }

        final var reward = takeProfit.subtract(entryPrice).abs();
        final var risk = entryPrice.subtract(stopLoss).abs();
        final var rrRatio = reward.divide(risk, 2, RoundingMode.HALF_UP);

        final var tradesUsedToday = journalService.getTodayTradeCount();
        final var dailyLoss = journalService.getTodayPnL();

        final var withinLimits = tradesUsedToday < tradingConfig.getMaxTradesPerDay()
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

    public boolean isRiskAcceptable(final RiskCalculation calculation) {
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
