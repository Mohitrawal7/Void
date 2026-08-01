package com.example.demo.service;

import com.example.demo.dto.cart.CartItemRequest;
import com.example.demo.dto.cart.CartResponse;
import com.example.demo.dto.order.OrderResponse;

public interface CartService {
    CartResponse getCart(String username);
    CartResponse addItem(String username, CartItemRequest request);
    CartResponse updateItemQuantity(String username, Long productId, int quantity);
    CartResponse removeItem(String username, Long productId);
    void clearCart(String username);
    OrderResponse checkout(String username);
}