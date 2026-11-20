package com.trading.simulator.controller;

import com.trading.simulator.dto.OrderRequest;
import com.trading.simulator.dto.OrderResponse;
import com.trading.simulator.dto.SubmitResponse;
import com.trading.simulator.engine.BookSnapshot;
import com.trading.simulator.engine.EngineStats;
import com.trading.simulator.engine.OrderStatus;
import com.trading.simulator.engine.Trade;
import com.trading.simulator.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Trading", description = "Orders, trades, order books and statistics")
public class OrderApiController {

    private final TradingService service;

    public OrderApiController(TradingService service) {
        this.service = service;
    }

    @Operation(summary = "Place a limit order and match it immediately")
    @PostMapping("/orders")
    public ResponseEntity<SubmitResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        SubmitResponse response = service.placeOrder(request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.order().id())).body(response);
    }

    @Operation(summary = "List orders, newest first (optional filters)")
    @GetMapping("/orders")
    public List<OrderResponse> listOrders(@RequestParam(required = false) String symbol,
                                          @RequestParam(required = false) String user,
                                          @RequestParam(required = false) OrderStatus status) {
        return service.listOrders(symbol, user, status);
    }

    @Operation(summary = "Get one order")
    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable long id) {
        return service.getOrder(id);
    }

    @Operation(summary = "Cancel an open order")
    @DeleteMapping("/orders/{id}")
    public OrderResponse cancelOrder(@PathVariable long id) {
        return service.cancelOrder(id);
    }

    @Operation(summary = "Recent trades, newest first")
    @GetMapping("/trades")
    public List<Trade> listTrades(@RequestParam(required = false) String symbol,
                                  @RequestParam(defaultValue = "50") int limit) {
        return service.listTrades(symbol, limit);
    }

    @Operation(summary = "Order book for a symbol (price levels)")
    @GetMapping("/orderbook/{symbol}")
    public BookSnapshot orderBook(@PathVariable String symbol) {
        return service.getOrderBook(symbol);
    }

    @Operation(summary = "Symbols that have orders")
    @GetMapping("/symbols")
    public List<String> symbols() {
        return service.getSymbols();
    }

    @Operation(summary = "Market statistics")
    @GetMapping("/stats")
    public EngineStats stats() {
        return service.getStats();
    }
}
