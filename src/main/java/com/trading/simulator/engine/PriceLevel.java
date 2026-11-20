package com.trading.simulator.engine;

import java.math.BigDecimal;

/** Total resting quantity at one price in an order book. */
public record PriceLevel(BigDecimal price, int totalQuantity, int orderCount) {
}
