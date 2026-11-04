package com.ayush.zepto.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ayush.zepto.catalog.dto.ProductRequest;
import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import com.ayush.zepto.catalog.repository.CategoryRepository;
import com.ayush.zepto.catalog.repository.ProductRepository;
import com.ayush.zepto.catalog.service.ProductService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/zepto_clone_test",
        "spring.datasource.username=${DB_USERNAME:root}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.cache.type=redis"
})
@EnabledIfEnvironmentVariable(named = "REDIS_CACHE_TESTS", matches = "true")
class RedisCacheIntegrationTest {
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired CacheManager cacheManager;
    @Autowired JdbcTemplate jdbc;

    private Long productId;
    private Long categoryId;

    @BeforeEach
    void createRows() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Category category = categories.saveAndFlush(new Category("Cache " + unique, "cache-" + unique));
        categoryId = category.getId();
        Product product = products.saveAndFlush(new Product("CACHE-" + unique, "Before cache", null, category,
                new BigDecimal("4.00")));
        productId = product.getId();
    }

    @AfterEach
    void deleteOnlyGeneratedRows() {
        if (productId != null) {
            var cache = cacheManager.getCache("product-detail");
            if (cache != null) cache.evict(productId);
            jdbc.update("DELETE FROM inventory WHERE product_id = ?", productId);
            products.deleteById(productId);
        }
        if (categoryId != null) categories.deleteById(categoryId);
    }

    @Test
    void productUpdatesEvictTheCachedCurrentRepresentation() {
        assertEquals("Before cache", productService.getActive(productId).name());
        Product product = products.findById(productId).orElseThrow();
        product.update(product.getSku(), "Database changed", product.getDescription(), product.getCategory(),
                new BigDecimal("4.00"));
        products.saveAndFlush(product);
        assertEquals("Before cache", productService.getActive(productId).name());

        productService.update(productId, new ProductRequest(product.getSku(), "After eviction", null, categoryId,
                new BigDecimal("5.00")));
        assertEquals("After eviction", productService.getActive(productId).name());
        assertEquals(new BigDecimal("5.00"), productService.getActive(productId).price());
    }
}
