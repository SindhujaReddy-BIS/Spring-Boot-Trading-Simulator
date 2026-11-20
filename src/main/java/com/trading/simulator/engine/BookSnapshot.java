package com.trading.simulator.engine;

import java.util.List;

/** Consistent view of one order book: bids best (highest) first, asks best (lowest) first. */
public record BookSnapshot(String symbol, List<PriceLevel> bids, List<PriceLevel> asks) {
}
