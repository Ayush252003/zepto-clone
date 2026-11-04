package com.ayush.zepto.catalog.repository;

import com.ayush.zepto.catalog.entity.Category;
import com.ayush.zepto.catalog.entity.Product;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> visibleProducts(String query, Long categoryId) {
        return catalogProducts(query, categoryId, true);
    }

    public static Specification<Product> adminProducts(String query, Long categoryId) {
        return catalogProducts(query, categoryId, false);
    }

    private static Specification<Product> catalogProducts(String query, Long categoryId, boolean visibleOnly) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            Join<Product, Category> category = root.join("category", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            if (visibleOnly) {
                predicates.add(criteriaBuilder.isTrue(root.get("active")));
                predicates.add(criteriaBuilder.isTrue(category.get("active")));
            }

            if (query != null && !query.isBlank()) {
                String term = query.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + term + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '!'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), pattern, '!')));
            }

            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(category.get("id"), categoryId));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
