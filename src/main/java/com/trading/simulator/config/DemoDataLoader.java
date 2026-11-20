package com.trading.simulator.config;

import com.trading.simulator.engine.MatchingEngine;
import com.trading.simulator.engine.OrderType;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Loads a few sample orders so the dashboard is not empty on first start. */
@Component
@ConditionalOnProperty(name = "trading.demo-data", havingValue = "true")
public class DemoDataLoader implements CommandLineRunner {

    private final MatchingEngine engine;

    public DemoDataLoader(MatchingEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(String... args) {
        engine.submit(OrderType.SELL, "Bindu", "AAPL", new BigDecimal("101.50"), 40);
        engine.submit(OrderType.SELL, "Ravi", "AAPL", new BigDecimal("102.00"), 25);
        engine.submit(OrderType.BUY, "Sindhu", "AAPL", new BigDecimal("99.00"), 30);
        engine.submit(OrderType.BUY, "Indu", "AAPL", new BigDecimal("101.50"), 15); // matches Bindu
        engine.submit(OrderType.SELL, "Anil", "MSFT", new BigDecimal("310.00"), 10);
        engine.submit(OrderType.BUY, "Meera", "MSFT", new BigDecimal("305.00"), 12);
    }
}
