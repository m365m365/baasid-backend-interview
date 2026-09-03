package com.trading.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderRequest {

    @NotNull(message = "商品 ID 不可為空")
    @Positive(message = "商品 ID 必須大於 0")
    private Long productId;

    @NotNull(message = "數量不可為空")
    @Positive(message = "數量必須大於 0")
    private Integer quantity;
}