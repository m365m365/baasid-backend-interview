package com.trading.platform.dto;

import java.math.BigDecimal;

public record ProductRequest(String name, BigDecimal price, Integer stock) {
}
