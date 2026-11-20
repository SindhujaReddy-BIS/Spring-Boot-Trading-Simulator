package com.trading.simulator.dto;

import com.trading.simulator.engine.Trade;
import java.util.List;

/** Result of placing an order: the order and any trades it executed immediately. */
public record SubmitResponse(OrderResponse order, List<Trade> trades) {
}
