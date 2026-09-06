package com.trading.platform.service;

import com.trading.platform.dto.ProductRequest;
import com.trading.platform.entity.Product;
import com.trading.platform.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;


    @PersistenceContext
    private EntityManager entityManager;

    public ProductService(ProductRepository productRepository,
                          AuditLogService auditLogService) {
        this.productRepository = productRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Product createProduct(ProductRequest request, String operator) {

        Product p = new Product();
        p.setName(request.name());
        p.setPrice(request.price());
        p.setStock(request.stock());

        Product savedProduct = productRepository.saveAndFlush(p);

        auditLogService.log(
                operator,
                "CREATE",
                "PRODUCT",
                savedProduct.getId(),
                null,
                savedProduct
        );

        return savedProduct;
    }

    @Transactional
    public Product updateProduct(Long id,
                                 ProductRequest request,
                                 String operator) {

        Product p = productRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Product not found")
                );

        Map<String, Object> before = new LinkedHashMap<>();

        before.put("id", p.getId());
        before.put("name", p.getName());
        before.put("price", p.getPrice());
        before.put("stock", p.getStock());
        before.put("version", p.getVersion());
        before.put("createdAt", p.getCreatedAt());

        p.setName(request.name());
        p.setPrice(request.price());
        p.setStock(request.stock());

        Product savedProduct = productRepository.saveAndFlush(p);

        auditLogService.log(
                operator,
                "UPDATE",
                "PRODUCT",
                savedProduct.getId(),
                before,
                savedProduct
        );

        return savedProduct;
    }

    @Transactional
    public void deleteProduct(Long id, String operator) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        auditLogService.log(
                operator,
                "DELETE",
                "PRODUCT",
                product.getId(),
                product,
                null
        );

        productRepository.delete(product);
    }

    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    // 已改用參數化查詢，可安全處理使用者輸入

    public List<Product> searchByName(String keyword) {
        String jpql = """
            SELECT p
            FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(:keyword)
            """;

        return entityManager
                .createQuery(jpql, Product.class)
                .setParameter("keyword", "%" + keyword.trim() + "%")
                .getResultList();
    }
}
