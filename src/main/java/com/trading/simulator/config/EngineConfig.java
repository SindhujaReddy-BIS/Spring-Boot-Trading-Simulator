package com.trading.simulator.config;

import com.trading.simulator.engine.MatchingEngine;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    /** One engine for the whole application. It holds all order books in memory. */
    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Trading Simulator API")
                .description("Place and cancel limit orders and read order books, trades and statistics. "
                        + "Orders are matched with price-time priority.")
                .version("1.0.0")
                .license(new License().name("MIT")));
    }
}
