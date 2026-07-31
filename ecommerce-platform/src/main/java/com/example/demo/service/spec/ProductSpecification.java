package com.example.demo.service.spec;

import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Builds dynamic WHERE clauses for product filtering/browsing.
 * Each method returns null when its criterion isn't supplied, so
 * Specification.where(...).and(...) simply skips it.
 */
public class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> {
            var join = root.<Product, Category>join("category");
            return cb.equal(join.get("id"), categoryId);
        };
    }

    public static Specification<Product> nameContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> build(Long categoryId, String keyword, BigDecimal minPrice, BigDecimal maxPrice) {
        return Specification.where(hasCategory(categoryId))
                .and(nameContains(keyword))
                .and(priceGreaterThanOrEqual(minPrice))
                .and(priceLessThanOrEqual(maxPrice));
    }
}
