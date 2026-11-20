package com.trading.simulator.engine;

import java.util.List;

/** Outcome of submitting an order: the order itself and the trades it caused. */
public record SubmitResult(Order order, List<Trade> trades) {
}
