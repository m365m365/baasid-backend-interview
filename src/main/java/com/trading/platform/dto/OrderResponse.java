package com.trading.platform.dto;

import java.math.BigDecimal;

public record OrderResponse(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal totalPrice
) {
}