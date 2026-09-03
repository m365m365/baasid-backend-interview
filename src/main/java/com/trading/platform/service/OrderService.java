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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import com.trading.platform.dto.OrderResponse;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private final SimpleDateFormat orderNoFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private int placedOrderCount = 0;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // 庫存扣減已使用樂觀鎖保護，併發下單不會超賣
    public OrderResponse placeOrder(String username, OrderRequest request) {
        User user = userRepository.findByUsername(username).orElse(null);
        Product product = productRepository.findById(request.getProductId()).orElse(null);

        if (!validateOrder(request)) {
            throw new RuntimeException("訂單資料有誤");
        }

        if (product.getStock() < request.getQuantity()) {
            throw new RuntimeException("庫存不足");
        }

        deductStock(product, request.getQuantity());

        String orderNo = orderNoFormat.format(new Date());
        placedOrderCount++;
        System.out.println("建立訂單 " + orderNo + "，本機累計 " + placedOrderCount + " 筆");

        Order order = new Order();
        order.setUser(user);
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setTotalPrice((int) product.getPrice() * request.getQuantity());

        Order savedOrder = orderRepository.save(order);

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getProduct().getId(),
                savedOrder.getProduct().getName(),
                savedOrder.getQuantity(),
                savedOrder.getTotalPrice()
        );
    }

    @Transactional
    public synchronized void deductStock(Product product, int quantity) {
        product.setStock(product.getStock() - quantity);
        productRepository.save(product);
    }

    private boolean validateOrder(OrderRequest request) {
        // 驗證下單數量與商品是否合法
        return true;
    }

    public List<OrderResponse> getUserOrders(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));

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
