package com.trading.simulator.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The buy and sell sides for ONE symbol. Every operation takes the book's lock, so
 * matching is atomic: an order is either matched completely against the book or not
 * at all, and two threads can never trade against the same resting quantity.
 *
 * <p>Matching rules: price-time priority. The best price wins; at the same price the
 * order that arrived first wins. A trade is executed at the price of the resting order.
 */
final class OrderBook {

    // Bids: highest price first. Asks: lowest price first. Ties: earliest arrival first.
    private static final Comparator<Order> BID_PRIORITY =
            Comparator.comparing(Order::getPrice, Comparator.<BigDecimal>reverseOrder())
                    .thenComparingLong(Order::getSequence);
    private static final Comparator<Order> ASK_PRIORITY =
            Comparator.comparing(Order::getPrice)
                    .thenComparingLong(Order::getSequence);

    private final String symbol;
    private final Lock lock = new ReentrantLock();
    private final PriorityQueue<Order> bids = new PriorityQueue<>(BID_PRIORITY);
    private final PriorityQueue<Order> asks = new PriorityQueue<>(ASK_PRIORITY);
    private long nextSequence = 0;

    OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /** Matches the incoming order against the opposite side, then rests any remainder. */
    List<Trade> submit(Order incoming, LongSupplier tradeIds, Consumer<Trade> tradeSink) {
        lock.lock();
        try {
            incoming.setSequence(nextSequence++);
            boolean isBuy = incoming.getType() == OrderType.BUY;
            PriorityQueue<Order> opposite = isBuy ? asks : bids;
            List<Trade> trades = new ArrayList<>();

            while (incoming.getRemaining() > 0 && !opposite.isEmpty()) {
                Order resting = opposite.peek();
                if (!crosses(incoming, resting)) {
                    break;
                }
                int quantity = Math.min(incoming.getRemaining(), resting.getRemaining());
                incoming.fill(quantity);
                resting.fill(quantity);

                Order buy = isBuy ? incoming : resting;
                Order sell = isBuy ? resting : incoming;
                Trade trade = new Trade(tradeIds.getAsLong(), symbol, resting.getPrice(), quantity,
                        buy.getUser(), sell.getUser(), buy.getId(), sell.getId(), Instant.now());
                trades.add(trade);
                tradeSink.accept(trade);

                if (resting.getRemaining() == 0) {
                    opposite.poll();
                }
            }

            if (incoming.getRemaining() > 0) {
                (isBuy ? bids : asks).add(incoming);
            }
            return trades;
        } finally {
            lock.unlock();
        }
    }

    /** Removes an open order from the book. Returns false if it was no longer open. */
    boolean cancel(Order order) {
        lock.lock();
        try {
            if (!order.isOpen()) {
                return false;
            }
            (order.getType() == OrderType.BUY ? bids : asks).remove(order);
            order.markCancelled();
            return true;
        } finally {
            lock.unlock();
        }
    }

    BookSnapshot snapshot() {
        lock.lock();
        try {
            return new BookSnapshot(symbol, levels(bids, BID_PRIORITY), levels(asks, ASK_PRIORITY));
        } finally {
            lock.unlock();
        }
    }

    private static boolean crosses(Order incoming, Order resting) {
        return incoming.getType() == OrderType.BUY
                ? incoming.getPrice().compareTo(resting.getPrice()) >= 0
                : incoming.getPrice().compareTo(resting.getPrice()) <= 0;
    }

    private static List<PriceLevel> levels(PriorityQueue<Order> side, Comparator<Order> priority) {
        List<Order> sorted = new ArrayList<>(side);
        sorted.sort(priority);
        Map<BigDecimal, int[]> grouped = new LinkedHashMap<>(); // price -> {quantity, count}
        for (Order order : sorted) {
            int[] level = grouped.computeIfAbsent(order.getPrice(), p -> new int[2]);
            level[0] += order.getRemaining();
            level[1]++;
        }
        List<PriceLevel> result = new ArrayList<>();
        grouped.forEach((price, level) -> result.add(new PriceLevel(price, level[0], level[1])));
        return result;
    }
}
