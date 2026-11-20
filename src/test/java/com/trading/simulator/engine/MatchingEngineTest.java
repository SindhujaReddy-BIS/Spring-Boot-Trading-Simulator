package com.trading.simulator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    private SubmitResult buy(String user, String symbol, String price, int quantity) {
        return engine.submit(OrderType.BUY, user, symbol, new BigDecimal(price), quantity);
    }

    private SubmitResult sell(String user, String symbol, String price, int quantity) {
        return engine.submit(OrderType.SELL, user, symbol, new BigDecimal(price), quantity);
    }

    @Test
    @DisplayName("Buy and sell at the same price match completely")
    void matchesAtSamePrice() {
        sell("bindu", "AAPL", "100", 10);
        SubmitResult result = buy("indu", "AAPL", "100", 10);

        assertEquals(1, result.trades().size());
        Trade trade = result.trades().get(0);
        assertEquals("indu", trade.buyer());
        assertEquals("bindu", trade.seller());
        assertEquals(10, trade.quantity());
        assertEquals(OrderStatus.FILLED, result.order().getStatus());
        assertTrue(engine.getBook("AAPL").asks().isEmpty());
    }

    @Test
    @DisplayName("Orders whose prices do not cross rest in the book")
    void noMatchWhenPricesDoNotCross() {
        sell("a", "AAPL", "101", 5);
        SubmitResult result = buy("b", "AAPL", "100", 5);

        assertTrue(result.trades().isEmpty());
        assertEquals(OrderStatus.OPEN, result.order().getStatus());
        BookSnapshot book = engine.getBook("AAPL");
        assertEquals(1, book.bids().size());
        assertEquals(1, book.asks().size());
    }

    @Test
    @DisplayName("A trade executes at the price of the resting order")
    void executesAtRestingPrice() {
        sell("a", "AAPL", "100", 5);
        SubmitResult result = buy("b", "AAPL", "105", 5);

        assertEquals(new BigDecimal("100.00"), result.trades().get(0).price());
    }

    @Test
    @DisplayName("A partial fill leaves the remainder in the book")
    void partialFill() {
        SubmitResult resting = sell("a", "AAPL", "100", 10);
        SubmitResult incoming = buy("b", "AAPL", "100", 4);

        assertEquals(OrderStatus.FILLED, incoming.order().getStatus());
        assertEquals(OrderStatus.PARTIALLY_FILLED, resting.order().getStatus());
        assertEquals(6, resting.order().getRemaining());
        assertEquals(6, engine.getBook("AAPL").asks().get(0).totalQuantity());
    }

    @Test
    @DisplayName("A large order sweeps several price levels")
    void sweepsMultipleLevels() {
        SubmitResult ask1 = sell("a", "AAPL", "100", 5);
        SubmitResult ask2 = sell("b", "AAPL", "101", 5);
        SubmitResult result = buy("c", "AAPL", "102", 8);

        assertEquals(2, result.trades().size());
        assertEquals(5, result.trades().get(0).quantity());
        assertEquals(new BigDecimal("100.00"), result.trades().get(0).price());
        assertEquals(3, result.trades().get(1).quantity());
        assertEquals(new BigDecimal("101.00"), result.trades().get(1).price());
        assertEquals(OrderStatus.FILLED, ask1.order().getStatus());
        assertEquals(2, ask2.order().getRemaining());
        assertEquals(OrderStatus.FILLED, result.order().getStatus());
    }

    @Test
    @DisplayName("An unfilled remainder of an aggressive order rests in the book")
    void remainderRests() {
        sell("a", "AAPL", "100", 3);
        SubmitResult result = buy("b", "AAPL", "100", 10);

        assertEquals(1, result.trades().size());
        assertEquals(OrderStatus.PARTIALLY_FILLED, result.order().getStatus());
        assertEquals(7, engine.getBook("AAPL").bids().get(0).totalQuantity());
    }

    @Test
    @DisplayName("At the same price, the earlier order is matched first")
    void timePriority() {
        sell("first", "AAPL", "100", 5);
        sell("second", "AAPL", "100", 5);
        SubmitResult result = buy("buyer", "AAPL", "100", 5);

        assertEquals("first", result.trades().get(0).seller());
    }

    @Test
    @DisplayName("The best price is matched first, regardless of arrival order")
    void pricePriority() {
        sell("expensive", "AAPL", "101", 5);
        sell("cheap", "AAPL", "100", 5);
        SubmitResult result = buy("buyer", "AAPL", "101", 5);

        assertEquals("cheap", result.trades().get(0).seller());
    }

    @Test
    @DisplayName("Symbols are separate markets and are case-insensitive")
    void symbolsAreIsolated() {
        sell("a", "AAPL", "100", 5);
        assertTrue(buy("b", "MSFT", "100", 5).trades().isEmpty());
        assertEquals(1, buy("c", "aapl", "100", 5).trades().size());
        assertEquals(List.of("AAPL", "MSFT"), engine.getSymbols());
    }

    @Test
    @DisplayName("Cancelling removes the order from the book")
    void cancelRemovesOrder() {
        SubmitResult placed = buy("a", "AAPL", "100", 5);
        Order cancelled = engine.cancel(placed.order().getId());

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertTrue(engine.getBook("AAPL").bids().isEmpty());
        assertTrue(sell("b", "AAPL", "100", 5).trades().isEmpty());
    }

    @Test
    @DisplayName("Filled, cancelled and unknown orders cannot be cancelled")
    void cancelErrors() {
        SubmitResult resting = sell("a", "AAPL", "100", 5);
        buy("b", "AAPL", "100", 5); // fills the resting order
        assertThrows(OrderNotCancellableException.class, () -> engine.cancel(resting.order().getId()));

        SubmitResult other = buy("c", "AAPL", "90", 5);
        engine.cancel(other.order().getId());
        assertThrows(OrderNotCancellableException.class, () -> engine.cancel(other.order().getId()));

        assertThrows(OrderNotFoundException.class, () -> engine.cancel(9999));
    }

    @Test
    @DisplayName("Invalid input is rejected")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> buy("a", "AAPL", "0", 5));
        assertThrows(IllegalArgumentException.class, () -> buy("a", "AAPL", "-1", 5));
        assertThrows(IllegalArgumentException.class, () -> buy("a", "AAPL", "10.123", 5));
        assertThrows(IllegalArgumentException.class, () -> buy("a", "AAPL", "10", 0));
        assertThrows(IllegalArgumentException.class, () -> buy("  ", "AAPL", "10", 5));
        assertThrows(IllegalArgumentException.class, () -> buy("a", "AA PL", "10", 5));
        assertThrows(IllegalArgumentException.class, () -> buy("a", "", "10", 5));
        assertThrows(IllegalArgumentException.class,
                () -> engine.submit(null, "a", "AAPL", BigDecimal.TEN, 5));
    }

    @Test
    @DisplayName("Trade history, filters and statistics are correct")
    void statisticsAndHistory() {
        sell("a", "AAPL", "100", 10);
        buy("b", "AAPL", "100", 4);
        buy("c", "AAPL", "100", 6);
        sell("d", "MSFT", "50", 1);

        EngineStats stats = engine.getStats();
        assertEquals(4, stats.totalOrders());
        assertEquals(1, stats.openOrders()); // only the MSFT sell is still open
        assertEquals(2, stats.totalTrades());
        assertEquals(10, stats.tradedQuantity());
        assertEquals(0, new BigDecimal("1000").compareTo(stats.tradedValue()));
        assertEquals(new BigDecimal("100.00"), stats.lastPrices().get("AAPL"));

        List<Trade> trades = engine.getTrades("aapl", 10);
        assertEquals(2, trades.size());
        assertEquals("c", trades.get(0).buyer()); // newest first
        assertEquals(1, engine.getTrades(null, 1).size());
        assertEquals(1, engine.findOrders("MSFT", null, null).size());
        assertEquals(1, engine.findOrders(null, "B", OrderStatus.FILLED).size());
    }

    @Test
    @DisplayName("Concurrent trading never creates or loses quantity")
    void concurrentSubmissions() throws Exception {
        int threads = 8;
        int ordersPerThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<List<SubmitResult>>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Callable<List<SubmitResult>> task = () -> {
                Random random = new Random(seed);
                List<SubmitResult> results = new ArrayList<>();
                for (int i = 0; i < ordersPerThread; i++) {
                    OrderType type = random.nextBoolean() ? OrderType.BUY : OrderType.SELL;
                    BigDecimal price = BigDecimal.valueOf(95 + random.nextInt(11)); // 95..105
                    int quantity = 1 + random.nextInt(20);
                    results.add(engine.submit(type, "user" + seed, "AAPL", price, quantity));
                }
                return results;
            };
            futures.add(pool.submit(task));
        }

        List<SubmitResult> all = new ArrayList<>();
        for (Future<List<SubmitResult>> future : futures) {
            all.addAll(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        long boughtQuantity = 0;
        long soldQuantity = 0;
        for (SubmitResult result : all) {
            Order order = result.order();
            assertTrue(order.getRemaining() >= 0, "order was over-filled");
            assertEquals(order.getQuantity(), order.getFilled() + order.getRemaining());
            if (order.getType() == OrderType.BUY) {
                boughtQuantity += order.getFilled();
            } else {
                soldQuantity += order.getFilled();
            }
        }

        EngineStats stats = engine.getStats();
        assertEquals(boughtQuantity, soldQuantity, "every share bought must have been sold");
        assertEquals(boughtQuantity, stats.tradedQuantity());
        assertEquals(threads * ordersPerThread, stats.totalOrders());

        BookSnapshot book = engine.getBook("AAPL");
        if (!book.bids().isEmpty() && !book.asks().isEmpty()) {
            assertTrue(book.bids().get(0).price().compareTo(book.asks().get(0).price()) < 0,
                    "the book must never be crossed after matching");
        }
        assertFalse(stats.tradedValue().signum() < 0);
    }
}
