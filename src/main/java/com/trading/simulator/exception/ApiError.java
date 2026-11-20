package com.trading.simulator.exception;

import java.time.Instant;
import java.util.Map;

/** JSON body returned for every REST error. */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> details) {
}
