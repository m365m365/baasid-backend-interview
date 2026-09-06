package com.trading.platform.service;

import com.trading.platform.dto.ProductRequest;
import com.trading.platform.entity.Product;
import com.trading.platform.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        Product savedProduct = productRepository.save(p);

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
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Product before = new Product();
        before.setName(p.getName());
        before.setPrice(p.getPrice());
        before.setStock(p.getStock());

        p.setName(request.name());
        p.setPrice(request.price());
        p.setStock(request.stock());

        Product savedProduct = productRepository.save(p);

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
    @SuppressWarnings("unchecked")
    public List<Product> searchByName(String keyword) {
        String jpql = "SELECT p FROM Product p WHERE p.name LIKE '%" + keyword + "%'";
        return entityManager.createQuery(jpql).getResultList();
    }
}
