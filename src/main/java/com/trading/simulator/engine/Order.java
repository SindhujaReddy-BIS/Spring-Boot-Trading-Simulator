package com.trading.simulator.engine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A limit order. Identity fields are immutable. The fill state is changed only by the
 * engine while it holds the lock of the order's book, and is volatile so other threads
 * can read a consistent value for display.
 */
public final class Order {

    private final long id;
    private final OrderType type;
    private final String user;
    private final String symbol;
    private final BigDecimal price;
    private final int quantity;
    private final Instant createdAt = Instant.now();

    private volatile int remaining;
    private volatile OrderStatus status = OrderStatus.OPEN;
    private long sequence; // arrival order inside the book, guarded by the book lock

    Order(long id, OrderType type, String user, String symbol, BigDecimal price, int quantity) {
        this.id = id;
        this.type = type;
        this.user = user;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.remaining = quantity;
    }

    public long getId() { return id; }
    public OrderType getType() { return type; }
    public String getUser() { return user; }
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public Instant getCreatedAt() { return createdAt; }
    public int getRemaining() { return remaining; }
    public int getFilled() { return quantity - remaining; }
    public OrderStatus getStatus() { return status; }

    /** True while the order can still be matched or cancelled. */
    public boolean isOpen() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    long getSequence() { return sequence; }

    void setSequence(long sequence) { this.sequence = sequence; }

    void fill(int amount) {
        remaining -= amount;
        status = remaining == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    void markCancelled() {
        status = OrderStatus.CANCELLED;
    }
}
