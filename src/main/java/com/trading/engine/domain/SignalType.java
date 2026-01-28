package com.trading.engine.domain;

/**
 * Three-tier signal classification system
 */
public enum SignalType {
    /**
     * Ready to trade - Quality A/B, all rules pass, has execution plan
     */
    TRADE,

    /**
     * Not ready but has potential - monitor conditions, prepare for entry
     */
    WATCH,

    /**
     * Hard block - rule violation or no viable improvement path
     */
    BLOCKED
}
