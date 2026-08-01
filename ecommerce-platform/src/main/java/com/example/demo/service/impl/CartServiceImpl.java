package com.example.demo.service.impl;

import com.example.demo.dto.cart.CartItemRequest;
import com.example.demo.dto.cart.CartItemResponse;
import com.example.demo.dto.cart.CartResponse;
import com.example.demo.dto.order.OrderItemRequest;
import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.entity.Product;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.CartService;
import com.example.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    @Value("${app.cache.cart-ttl-seconds:86400}")
    private long cartTtlSeconds;

    private String cartKey(String username) {
        return "cart:" + username;
    }

    private HashOperations<String, Object, Object> hashOps() {
        return redisTemplate.opsForHash();
    }

    @Override
    public CartResponse getCart(String username) {
        Map<Object, Object> raw = hashOps().entries(cartKey(username));
        return buildResponse(raw);
    }

    @Override
    public CartResponse addItem(String username, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new BadRequestException("Insufficient stock for product: " + product.getName());
        }

        String key = cartKey(username);
        hashOps().increment(key, String.valueOf(request.getProductId()), request.getQuantity());
        redisTemplate.expire(key, Duration.ofSeconds(cartTtlSeconds)); // refresh TTL on activity

        return getCart(username);
    }

    @Override
    public CartResponse updateItemQuantity(String username, Long productId, int quantity) {
        String key = cartKey(username);
        if (quantity <= 0) {
            hashOps().delete(key, String.valueOf(productId));
        } else {
            hashOps().put(key, String.valueOf(productId), quantity);
        }
        redisTemplate.expire(key, Duration.ofSeconds(cartTtlSeconds));
        return getCart(username);
    }

    @Override
    public CartResponse removeItem(String username, Long productId) {
        hashOps().delete(cartKey(username), String.valueOf(productId));
        return getCart(username);
    }

    @Override
    public void clearCart(String username) {
        redisTemplate.delete(cartKey(username));
    }

    @Override
    public OrderResponse checkout(String username) {
        Map<Object, Object> raw = hashOps().entries(cartKey(username));
        if (raw.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        List<OrderItemRequest> items = raw.entrySet().stream()
                .map(e -> {
                    OrderItemRequest item = new OrderItemRequest();
                    item.setProductId(Long.valueOf(e.getKey().toString()));
                    item.setQuantity(Integer.parseInt(e.getValue().toString()));
                    return item;
                })
                .toList();

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setItems(items);

        OrderResponse response = orderService.createOrder(username, orderRequest);
        clearCart(username); // only clear after order succeeds
        return response;
    }

    private CartResponse buildResponse(Map<Object, Object> raw) {
        BigDecimal total = BigDecimal.ZERO;
        List<CartItemResponse> items = new java.util.ArrayList<>();

        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            Long productId = Long.valueOf(entry.getKey().toString());
            int quantity = Integer.parseInt(entry.getValue().toString());

            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) continue; // product deleted since being added to cart

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(subtotal);

            items.add(CartItemResponse.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(quantity)
                    .subtotal(subtotal)
                    .build());
        }

        return CartResponse.builder()
                .items(items)
                .totalAmount(total)
                .build();
    }
}