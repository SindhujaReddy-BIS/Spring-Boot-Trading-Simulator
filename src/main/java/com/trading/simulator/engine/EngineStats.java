package com.trading.simulator.engine;

import java.math.BigDecimal;
import java.util.Map;

/** Aggregate numbers about the whole market. */
public record EngineStats(
        long totalOrders,
        long openOrders,
        long totalTrades,
        long tradedQuantity,
        BigDecimal tradedValue,
        Map<String, BigDecimal> lastPrices) {
}
