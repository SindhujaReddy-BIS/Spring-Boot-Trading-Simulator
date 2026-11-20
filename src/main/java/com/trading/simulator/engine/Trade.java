package com.trading.simulator.engine;

import java.math.BigDecimal;
import java.time.Instant;

/** An executed trade between one buy order and one sell order. Immutable. */
public record Trade(
        long id,
        String symbol,
        BigDecimal price,
        int quantity,
        String buyer,
        String seller,
        long buyOrderId,
        long sellOrderId,
        Instant executedAt) {
}
