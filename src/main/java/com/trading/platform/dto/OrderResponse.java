package com.trading.platform.dto;

public record OrderResponse(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        double totalPrice
) {
}