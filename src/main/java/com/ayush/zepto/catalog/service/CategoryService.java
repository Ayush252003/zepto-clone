package com.ayush.zepto.catalog.service;

import com.ayush.zepto.catalog.dto.CategoryRequest;
import com.ayush.zepto.catalog.dto.CategoryResponse;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "categories-active")
    public List<CategoryResponse> listActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories-active", "product-detail"}, allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        ensureUnique(name, slug, null);
        return toResponse(categoryRepository.saveAndFlush(new Category(name, slug)));
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories-active", "product-detail"}, allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        String name = request.name().trim();
        String slug = request.slug().trim().toLowerCase(Locale.ROOT);
        ensureUnique(name, slug, id);
        category.update(name, slug);
        return toResponse(categoryRepository.saveAndFlush(category));
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories-active", "product-detail"}, allEntries = true)
    public void deactivate(Long id) {
        Category category = categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (productRepository.existsByCategory_IdAndActiveTrue(id)) {
            throw new ResourceConflictException("Deactivate or move active products before deactivating this category.");
        }
        category.deactivate();
        categoryRepository.saveAndFlush(category);
    }

    @Transactional
    @CacheEvict(cacheNames = {"categories-active", "product-detail"}, allEntries = true)
    public void setActive(Long id, boolean active) {
        Category category = categoryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (!active && productRepository.existsByCategory_IdAndActiveTrue(id)) {
            throw new ResourceConflictException("Deactivate or move active products before deactivating this category.");
        }
        if (active) {
            category.activate();
        } else {
            category.deactivate();
        }
        categoryRepository.saveAndFlush(category);
    }

    private void ensureUnique(String name, String slug, Long excludedId) {
        boolean nameExists = excludedId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        boolean slugExists = excludedId == null
                ? categoryRepository.existsBySlugIgnoreCase(slug)
                : categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, excludedId);
        if (nameExists || slugExists) {
            throw new ResourceConflictException("A category with that name or slug already exists.");
        }
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.isActive());
    }
}
