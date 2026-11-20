package com.trading.simulator.dto;

import com.trading.simulator.engine.Order;
import com.trading.simulator.engine.OrderStatus;
import com.trading.simulator.engine.OrderType;
import java.math.BigDecimal;
import java.time.Instant;

/** Read-only view of an order. */
public record OrderResponse(
        long id,
        OrderType type,
        String user,
        String symbol,
        BigDecimal price,
        int quantity,
        int filledQuantity,
        int remainingQuantity,
        OrderStatus status,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        int remaining = order.getRemaining();
        return new OrderResponse(order.getId(), order.getType(), order.getUser(), order.getSymbol(),
                order.getPrice(), order.getQuantity(), order.getQuantity() - remaining, remaining,
                order.getStatus(), order.getCreatedAt());
    }
}
