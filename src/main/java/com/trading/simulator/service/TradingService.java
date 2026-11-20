package com.trading.simulator.service;

import com.trading.simulator.dto.OrderRequest;
import com.trading.simulator.dto.OrderResponse;
import com.trading.simulator.dto.SubmitResponse;
import com.trading.simulator.engine.BookSnapshot;
import com.trading.simulator.engine.EngineStats;
import com.trading.simulator.engine.MatchingEngine;
import com.trading.simulator.engine.OrderStatus;
import com.trading.simulator.engine.SubmitResult;
import com.trading.simulator.engine.Trade;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Application layer between the controllers and the matching engine. */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);
    private static final int MAX_TRADES_PER_REQUEST = 500;

    private final MatchingEngine engine;

    public TradingService(MatchingEngine engine) {
        this.engine = engine;
    }

    public SubmitResponse placeOrder(OrderRequest request) {
        SubmitResult result = engine.submit(request.type(), request.user(), request.symbol(),
                request.price(), request.quantity());
        log.info("Order {} {} {} {} @ {} -> {} ({} trades)", result.order().getId(), request.type(),
                request.quantity(), result.order().getSymbol(), result.order().getPrice(),
                result.order().getStatus(), result.trades().size());
        return new SubmitResponse(OrderResponse.from(result.order()), result.trades());
    }

    public OrderResponse cancelOrder(long orderId) {
        OrderResponse response = OrderResponse.from(engine.cancel(orderId));
        log.info("Order {} cancelled", orderId);
        return response;
    }

    public OrderResponse getOrder(long orderId) {
        return OrderResponse.from(engine.getOrder(orderId));
    }

    public List<OrderResponse> listOrders(String symbol, String user, OrderStatus status) {
        return engine.findOrders(symbol, user, status).stream().map(OrderResponse::from).toList();
    }

    public List<Trade> listTrades(String symbol, int limit) {
        return engine.getTrades(symbol, Math.max(1, Math.min(limit, MAX_TRADES_PER_REQUEST)));
    }

    public BookSnapshot getOrderBook(String symbol) {
        return engine.getBook(symbol);
    }

    public List<BookSnapshot> getAllOrderBooks() {
        return engine.getSymbols().stream().map(engine::getBook).toList();
    }

    public List<String> getSymbols() {
        return engine.getSymbols();
    }

    public EngineStats getStats() {
        return engine.getStats();
    }
}
