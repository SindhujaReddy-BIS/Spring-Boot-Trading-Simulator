package com.trading.simulator.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * In-memory matching engine for many symbols. It is plain Java (no Spring) so it can be
 * tested on its own.
 *
 * <p>Thread safety: each symbol has its own {@link OrderBook} with its own lock, so orders
 * for different symbols are matched in parallel, while orders for the same symbol are
 * matched one at a time.
 */
public class MatchingEngine {

    /** Only the most recent trades are kept in memory. */
    static final int MAX_TRADE_HISTORY = 10_000;
    private static final Pattern SYMBOL = Pattern.compile("[A-Za-z]{1,10}");

    private final ConcurrentMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong orderIds = new AtomicLong();
    private final AtomicLong tradeIds = new AtomicLong();

    // Trade history and totals. All guarded by synchronizing on tradeHistory.
    private final Deque<Trade> tradeHistory = new ArrayDeque<>(); // newest first
    private final Map<String, BigDecimal> lastPrices = new TreeMap<>();
    private long tradedQuantity;
    private long tradeCount;
    private BigDecimal tradedValue = BigDecimal.ZERO;

    /**
     * Submits a limit order and matches it immediately.
     *
     * @throws IllegalArgumentException if any value is invalid
     */
    public SubmitResult submit(OrderType type, String user, String symbol, BigDecimal price, int quantity) {
        if (type == null) {
            throw new IllegalArgumentException("Order type is required");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("User is required");
        }
        if (symbol == null || !SYMBOL.matcher(symbol.trim()).matches()) {
            throw new IllegalArgumentException("Symbol must be 1 to 10 letters");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Price can have at most 2 decimal places");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        Order order = new Order(orderIds.incrementAndGet(), type, user.trim(), normalizedSymbol,
                price.setScale(2, RoundingMode.UNNECESSARY), quantity);
        orders.put(order.getId(), order);

        OrderBook book = books.computeIfAbsent(normalizedSymbol, OrderBook::new);
        List<Trade> trades = book.submit(order, tradeIds::incrementAndGet, this::recordTrade);
        return new SubmitResult(order, trades);
    }

    /**
     * Cancels an order that is still open.
     *
     * @throws OrderNotFoundException       if no such order exists
     * @throws OrderNotCancellableException if it is already filled or cancelled
     */
    public Order cancel(long orderId) {
        Order order = getOrder(orderId);
        OrderBook book = books.get(order.getSymbol());
        if (!book.cancel(order)) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }
        return order;
    }

    public Order getOrder(long orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    /** Orders, newest first. Any filter may be null. */
    public List<Order> findOrders(String symbol, String user, OrderStatus status) {
        String wantedSymbol = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        List<Order> result = new ArrayList<>();
        for (Order order : orders.values()) {
            if ((wantedSymbol == null || order.getSymbol().equals(wantedSymbol))
                    && (user == null || user.isBlank() || order.getUser().equalsIgnoreCase(user.trim()))
                    && (status == null || order.getStatus() == status)) {
                result.add(order);
            }
        }
        result.sort(Comparator.comparingLong(Order::getId).reversed());
        return result;
    }

    /** Recent trades, newest first. The symbol filter may be null. */
    public List<Trade> getTrades(String symbol, int limit) {
        String wantedSymbol = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        List<Trade> result = new ArrayList<>();
        synchronized (tradeHistory) {
            for (Trade trade : tradeHistory) {
                if (result.size() >= limit) {
                    break;
                }
                if (wantedSymbol == null || trade.symbol().equals(wantedSymbol)) {
                    result.add(trade);
                }
            }
        }
        return result;
    }

    /** Symbols that have received at least one order, sorted. */
    public List<String> getSymbols() {
        List<String> symbols = new ArrayList<>(books.keySet());
        symbols.sort(Comparator.naturalOrder());
        return symbols;
    }

    public BookSnapshot getBook(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        OrderBook book = books.get(normalized);
        return book != null ? book.snapshot() : new BookSnapshot(normalized, List.of(), List.of());
    }

    public EngineStats getStats() {
        long open = orders.values().stream().filter(Order::isOpen).count();
        synchronized (tradeHistory) {
            return new EngineStats(orders.size(), open, tradeCount, tradedQuantity, tradedValue,
                    new TreeMap<>(lastPrices));
        }
    }

    /** Called by the order book, while it holds its lock, for every executed trade. */
    private void recordTrade(Trade trade) {
        synchronized (tradeHistory) {
            tradeHistory.addFirst(trade);
            if (tradeHistory.size() > MAX_TRADE_HISTORY) {
                tradeHistory.removeLast();
            }
            tradeCount++;
            tradedQuantity += trade.quantity();
            tradedValue = tradedValue.add(trade.price().multiply(BigDecimal.valueOf(trade.quantity())));
            lastPrices.put(trade.symbol(), trade.price());
        }
    }
}
