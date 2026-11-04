package com.ayush.zepto.catalog.controller;

import com.ayush.zepto.catalog.dto.CategoryRequest;
import com.ayush.zepto.catalog.dto.CategoryResponse;
import com.ayush.zepto.catalog.dto.ActiveStatusRequest;
import com.ayush.zepto.catalog.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listActive() {
        return categoryService.listActive();
    }

    @GetMapping("/admin/categories")
    public List<CategoryResponse> listAll() {
        return categoryService.listAll();
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.status(201).body(category);
    }

    @PutMapping("/admin/categories/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/categories/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable Long id, @Valid @RequestBody ActiveStatusRequest request) {
        categoryService.setActive(id, request.active());
        return ResponseEntity.noContent().build();
    }
}
