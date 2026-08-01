package com.example.demo.service.impl;

import com.example.demo.dto.product.CategoryDto;
import com.example.demo.dto.product.ProductDto;
import com.example.demo.dto.product.ProductRequest;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.ProductService;
import com.example.demo.service.cache.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCacheService productCacheService;

    @Value("${app.cache.product-list-ttl-seconds:120}")
    private long listTtlSeconds;
    @Value("${app.cache.product-item-ttl-seconds:600}")
    private long itemTtlSeconds;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ProductDto> getProducts(){

        String cacheKey = productCacheService.buildListKey();

        List<ProductDto> cached = productCacheService.getList(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ProductDto> result = productRepository.findAll().stream()
                        .map(p -> ProductDto.builder()
                                .id(p.getId())
                                .name(p.getName())
                                .price(p.getPrice())
                                .categoryId(p.getCategory().getId())
                                .categoryName(p.getCategory().getName())
                                .description(p.getDescription())
                                .createdAt(p.getCreatedAt())
                                .imageUrl(p.getImageUrl())
                                .stockQuantity(p.getStockQuantity())
                                .build())
                .toList();


        productCacheService.putList(cacheKey, result, listTtlSeconds);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        ProductDto cached = productCacheService.getItem(id);
        if (cached != null) {
            return cached;
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        ProductDto dto = toDto(product);
        productCacheService.putItem(id, dto, itemTtlSeconds);
        return dto;
    }

    @Override
    @Transactional
    public ProductDto createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .imageUrl(request.getImageUrl())
                .category(category)
                .build();

        Product saved = productRepository.save(product);
        productCacheService.invalidateProduct(saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ProductDto updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrl(request.getImageUrl());
        product.setCategory(category);

        Product saved = productRepository.save(product);
        productCacheService.invalidateProduct(saved.getId());
        return toDto(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
        productCacheService.invalidateProduct(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> CategoryDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription()).build())
                        .toList();
            }



    @Override
    @Transactional
    public CategoryDto createCategory(CategoryDto categoryDto) {
        if (categoryRepository.existsByNameIgnoreCase(categoryDto.getName())) {
            throw new BadRequestException("Category already exists: " + categoryDto.getName());
        }
        Category category = Category.builder()
                .name(categoryDto.getName())
                .description(categoryDto.getDescription())
                .build();
        Category saved = categoryRepository.save(category);
        return CategoryDto.builder().id(saved.getId()).name(saved.getName()).description(saved.getDescription()).build();
    }

    private ProductDto toDto(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .imageUrl(product.getImageUrl())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .createdAt(product.getCreatedAt())
                .build();
    }
}