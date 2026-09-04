package com.trading.platform.service;

import com.trading.platform.dto.OrderRequest;
import com.trading.platform.entity.Order;
import com.trading.platform.entity.Product;
import com.trading.platform.entity.User;
import com.trading.platform.repository.OrderRepository;
import com.trading.platform.repository.ProductRepository;
import com.trading.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.trading.platform.dto.OrderResponse;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;



    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // 下單與庫存扣減在同一交易中，Product 使用 @Version 防止併發遺失更新
    @Transactional
    public OrderResponse placeOrder(String username, OrderRequest request) {

        if (!validateOrder(request)) {
            throw new IllegalArgumentException("訂單資料有誤");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("使用者不存在")
                );

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() ->
                        new IllegalArgumentException("商品不存在")
                );

        if (product.getStock() < request.getQuantity()) {
            throw new IllegalStateException("庫存不足");
        }

        product.setStock(
                product.getStock() - request.getQuantity()
        );

        Order order = new Order();
        order.setUser(user);
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(
                product.getPrice() * request.getQuantity()
        );

        Order savedOrder = orderRepository.save(order);

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getProduct().getId(),
                savedOrder.getProduct().getName(),
                savedOrder.getQuantity(),
                savedOrder.getTotalPrice()
        );
    }



    private boolean validateOrder(OrderRequest request) {
        return request != null
                && request.getProductId() != null
                && request.getQuantity() != null
                && request.getQuantity() > 0;
    }

    public List<OrderResponse> getUserOrders(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("使用者不存在")
                );

        List<Order> orders = orderRepository.findByUserId(user.getId());

        return orders.stream()
                .map(order -> new OrderResponse(
                        order.getId(),
                        order.getProduct().getId(),
                        order.getProduct().getName(),
                        order.getQuantity(),
                        order.getTotalPrice()
                ))
                .toList();
    }
}
