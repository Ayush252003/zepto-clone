package com.ayush.zepto.catalog.service;

import com.ayush.zepto.catalog.dto.CategorySummary;
import com.ayush.zepto.catalog.dto.PageResponse;
import com.ayush.zepto.catalog.dto.ProductRequest;
import com.ayush.zepto.catalog.dto.ProductResponse;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.catalog.repository.ProductSpecifications;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.inventory.service.InventoryService;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

@Service
public class ProductService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SORT_FIELDS = Set.of("name", "price", "createdAt");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
            InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String query, Long categoryId, int page, int size, String sort) {
        return list(query, categoryId, page, size, sort, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> listAdmin(String query, Long categoryId, int page, int size, String sort) {
        return list(query, categoryId, page, size, sort, false);
    }

    private PageResponse<ProductResponse> list(
            String query, Long categoryId, int page, int size, String sort, boolean visibleOnly) {
        if (page < 0) {
            throw new BadRequestException("page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (query != null && query.length() > 100) {
            throw new BadRequestException("q must be at most 100 characters.");
        }
        if (categoryId != null && categoryId < 1) {
            throw new BadRequestException("categoryId must be positive.");
        }

        Sort requestedSort = parseSort(sort);
        Sort stableSort = requestedSort.and(Sort.by(Sort.Direction.ASC, "id"));
        Specification<Product> specification = visibleOnly
                ? ProductSpecifications.visibleProducts(query, categoryId)
                : ProductSpecifications.adminProducts(query, categoryId);
        Page<ProductResponse> results = productRepository
                .findAll(specification, PageRequest.of(page, size, stableSort))
                .map(this::toResponse);
        return PageResponse.from(results);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "product-detail", key = "#id")
    public ProductResponse getActive(Long id) {
        Product product = productRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getAdmin(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = request.sku().trim();
        ensureSkuAvailable(sku, null);
        Category category = activeCategoryForUpdate(request.categoryId());
        Product product = new Product(sku, request.name().trim(), cleanDescription(request.description()),
                category, request.price());
        Product savedProduct = productRepository.saveAndFlush(product);
        inventoryService.initialize(savedProduct);
        return toResponse(savedProduct);
    }

    @Transactional
    @CacheEvict(cacheNames = "product-detail", key = "#id")
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        String sku = request.sku().trim();
        ensureSkuAvailable(sku, id);
        Category category = activeCategoryForUpdate(request.categoryId());
        product.update(sku, request.name().trim(), cleanDescription(request.description()), category, request.price());
        return toResponse(productRepository.saveAndFlush(product));
    }

    @Transactional
    @CacheEvict(cacheNames = "product-detail", key = "#id")
    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        product.deactivate();
        productRepository.saveAndFlush(product);
    }

    @Transactional
    @CacheEvict(cacheNames = "product-detail", key = "#id")
    public void setActive(Long id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        if (active) {
            activeCategoryForUpdate(product.getCategory().getId());
            product.activate();
        } else {
            product.deactivate();
        }
        productRepository.saveAndFlush(product);
    }

    private Category activeCategoryForUpdate(Long categoryId) {
        Category category = categoryRepository.findByIdForUpdate(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (!category.isActive()) {
            throw new ResourceConflictException("Products must belong to an active category.");
        }
        return category;
    }

    private void ensureSkuAvailable(String sku, Long excludedId) {
        boolean exists = excludedId == null
                ? productRepository.existsBySkuIgnoreCase(sku)
                : productRepository.existsBySkuIgnoreCaseAndIdNot(sku, excludedId);
        if (exists) {
            throw new ResourceConflictException("A product with that SKU already exists.");
        }
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "name");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw new BadRequestException("sort must use field,direction format.");
        }
        if (!SORT_FIELDS.contains(parts[0])) {
            throw new BadRequestException("sort field must be one of name, price, or createdAt.");
        }
        try {
            return Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("sort direction must be asc or desc.");
        }
    }

    private String cleanDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                new CategorySummary(category.getId(), category.getName()), product.getPrice(), "INR", product.isActive());
    }
}
