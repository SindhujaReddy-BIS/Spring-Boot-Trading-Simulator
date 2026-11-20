package com.trading.simulator.engine;

public class OrderNotCancellableException extends RuntimeException {
    public OrderNotCancellableException(long orderId, OrderStatus status) {
        super("Order " + orderId + " cannot be cancelled because it is " + status);
    }
}
