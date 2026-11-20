package com.trading.simulator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests of the REST API. The application context (and the in-memory market) is
 * shared by all tests, so each test uses its own symbol.
 */
@SpringBootTest(properties = "trading.demo-data=false")
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String order(String type, String user, String symbol, String price, int quantity) {
        return """
                {"type":"%s","user":"%s","symbol":"%s","price":%s,"quantity":%d}
                """.formatted(type, user, symbol, price, quantity);
    }

    @Test
    @DisplayName("POST /api/orders creates an open order")
    void placeOrder() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("BUY", "sindhu", "AAA", "50.00", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.status").value("OPEN"))
                .andExpect(jsonPath("$.order.symbol").value("AAA"))
                .andExpect(jsonPath("$.trades.length()").value(0));
    }

    @Test
    @DisplayName("A matching order produces a trade")
    void matchingOrdersTrade() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("SELL", "bindu", "BBB", "40.00", 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("buy", "indu", "BBB", "41.00", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.status").value("FILLED"))
                .andExpect(jsonPath("$.trades.length()").value(1))
                .andExpect(jsonPath("$.trades[0].buyer").value("indu"))
                .andExpect(jsonPath("$.trades[0].seller").value("bindu"))
                .andExpect(jsonPath("$.trades[0].price").value(40.00));

        mockMvc.perform(get("/api/trades").param("symbol", "BBB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Invalid input returns 400 with field messages")
    void validationErrors() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("BUY", "", "CCC", "-5", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.user").exists())
                .andExpect(jsonPath("$.details.price").exists())
                .andExpect(jsonPath("$.details.quantity").exists());
    }

    @Test
    @DisplayName("An unknown order type returns 400")
    void invalidType() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("HOLD", "a", "DDD", "10", 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} cancels, and cancelling twice returns 409")
    void cancelOrder() throws Exception {
        String body = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(order("BUY", "a", "EEE", "10.00", 3)))
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(body, "$.order.id");

        mockMvc.perform(delete("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(delete("/api/orders/" + id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Unknown order returns 404")
    void unknownOrder() throws Exception {
        mockMvc.perform(get("/api/orders/987654"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/orderbook/{symbol} aggregates price levels")
    void orderBook() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("BUY", "a", "FFF", "10.00", 3))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(order("BUY", "b", "FFF", "10.00", 4))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/orderbook/fff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("FFF"))
                .andExpect(jsonPath("$.bids[0].totalQuantity").value(7))
                .andExpect(jsonPath("$.bids[0].orderCount").value(2));
    }

    @Test
    @DisplayName("The dashboard page renders")
    void dashboard() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }
}
