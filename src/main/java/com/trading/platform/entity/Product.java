package com.trading.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal price;

    private Integer stock;

    @Version
    private Long version;

    private LocalDateTime createdAt = LocalDateTime.now();
}
