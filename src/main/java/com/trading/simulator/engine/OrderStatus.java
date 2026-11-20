package com.trading.simulator.engine;

/** Lifecycle of an order inside the matching engine. */
public enum OrderStatus {
    OPEN, PARTIALLY_FILLED, FILLED, CANCELLED
}
