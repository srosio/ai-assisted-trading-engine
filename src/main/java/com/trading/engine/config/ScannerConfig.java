package com.trading.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "scanner")
@Data
public class ScannerConfig {

    private boolean enabled = true;

    // Watchlist of symbols to scan every session
    private List<String> watchlist = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
            "XRPUSDT", "DOGEUSDT", "ADAUSDT", "AVAXUSDT",
            "LINKUSDT", "DOTUSDT"
    );

    // Session schedule (UTC hours) - when to trigger scans
    private int londonOpenHour = 8;
    private int nyOpenHour = 13;

    // Pre-screening thresholds (filter before AI to save costs)
    private double minOiChangeForScreen = 1.5;       // Min absolute OI change % to be "interesting"
    private double minVolumeRatioForScreen = 1.2;     // Volume vs avg ratio to consider
    private double keyLevelProximityPercent = 1.0;    // % distance from key level to flag

    // AI discovery settings
    private int maxSymbolsPerScan = 5;                // Max symbols to send to AI per scan (cost control)
    private int maxTokensForDiscovery = 2048;         // More tokens for discovery vs assessment

    // Scan behavior
    private boolean sendSessionSummary = true;        // Send Telegram summary after each scan
    private boolean onlySendTradeableSignals = false;  // If true, only notify TRADE signals

    // Timeframes to analyze
    private List<String> timeframes = List.of("15m", "1h", "4h");
}
