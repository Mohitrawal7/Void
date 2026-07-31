package com.example.demo.service;

import com.example.demo.dto.product.CategoryDto;
import com.example.demo.dto.product.ProductDto;
import com.example.demo.dto.product.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    Page<ProductDto> getProducts(Long categoryId, String keyword, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    ProductDto getProductById(Long id);

    ProductDto createProduct(ProductRequest request);

    ProductDto updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);

    List<CategoryDto> getAllCategories();

    CategoryDto createCategory(CategoryDto categoryDto);
}
