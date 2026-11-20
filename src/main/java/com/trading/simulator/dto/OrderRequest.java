package com.trading.simulator.dto;

import com.trading.simulator.engine.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Input for placing an order, used by both the REST API and the web form. */
public record OrderRequest(
        @NotNull(message = "type is required (BUY or SELL)")
        OrderType type,

        @NotBlank(message = "user is required")
        @Size(max = 30, message = "user must be at most 30 characters")
        String user,

        @NotBlank(message = "symbol is required")
        @Pattern(regexp = "^[A-Za-z]{1,10}$", message = "symbol must be 1 to 10 letters")
        String symbol,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be at least 0.01")
        @Digits(integer = 9, fraction = 2, message = "price can have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 1_000_000, message = "quantity must be at most 1,000,000")
        Integer quantity) {
}
