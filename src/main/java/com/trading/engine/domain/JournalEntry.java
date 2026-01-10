package com.trading.engine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "journal_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", unique = true, nullable = false)
    private String signalId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private String event;

    @Column(name = "webhook_payload", columnDefinition = "TEXT")
    private String webhookPayload;

    @Column(name = "market_context", columnDefinition = "TEXT")
    private String marketContext;

    @Column(name = "ai_assessment", columnDefinition = "TEXT")
    private String aiAssessment;

    @Column(name = "rule_result", columnDefinition = "TEXT")
    private String ruleResult;

    @Column(name = "setup_quality")
    private String setupQuality;

    @Column(name = "rules_passed")
    private Boolean rulesPassed;

    @Column(name = "entry_price", precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "stop_loss", precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(name = "take_profit", precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Column(name = "position_size", precision = 20, scale = 8)
    private BigDecimal positionSize;

    @Column(name = "risk_reward_ratio", precision = 10, scale = 2)
    private BigDecimal riskRewardRatio;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "session")
    private String session;

    @Column(name = "status")
    private String status;

    // Manual fields (filled later by trader)
    @Column(name = "trade_taken")
    private Boolean tradeTaken;

    @Column(name = "exit_price", precision = 20, scale = 8)
    private BigDecimal exitPrice;

    @Column(name = "pnl", precision = 20, scale = 8)
    private BigDecimal pnl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "outcome")
    private String outcome; // WIN, LOSS, BREAKEVEN, NOT_TAKEN

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
