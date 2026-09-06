package com.trading.platform.controller;

import com.trading.platform.dto.ProductRequest;
import com.trading.platform.entity.Product;
import com.trading.platform.service.ProductService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product create(@RequestBody ProductRequest request,
                          Authentication authentication) {

        return productService.createProduct(
                request,
                authentication.getName()
        );
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id,
                          @RequestBody ProductRequest request,
                          Authentication authentication) {

        return productService.updateProduct(
                id,
                request,
                authentication.getName()
        );
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         Authentication authentication) {

        productService.deleteProduct(
                id,
                authentication.getName()
        );

        return "deleted";
    }

    @GetMapping
    public List<Product> list() {
        return productService.listProducts();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String keyword) {
        return productService.searchByName(keyword);
    }
}
