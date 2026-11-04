package com.ayush.zepto.catalog.controller;

import com.ayush.zepto.catalog.dto.PageResponse;
import com.ayush.zepto.catalog.dto.ActiveStatusRequest;
import com.ayush.zepto.catalog.dto.ProductRequest;
import com.ayush.zepto.catalog.dto.ProductResponse;
import com.ayush.zepto.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return productService.list(q, categoryId, page, size, sort);
    }

    @GetMapping("/admin/products")
    public PageResponse<ProductResponse> listAdmin(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return productService.listAdmin(q, categoryId, page, size, sort);
    }

    @GetMapping("/products/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getActive(id);
    }

    @GetMapping("/admin/products/{id}")
    public ProductResponse getAdmin(@PathVariable Long id) {
        return productService.getAdmin(id);
    }

    @PostMapping("/admin/products")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/products/{id}").buildAndExpand(product.id()).toUri();
        return ResponseEntity.created(location).body(product);
    }

    @PutMapping("/admin/products/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/products/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable Long id, @Valid @RequestBody ActiveStatusRequest request) {
        productService.setActive(id, request.active());
        return ResponseEntity.noContent().build();
    }
}
