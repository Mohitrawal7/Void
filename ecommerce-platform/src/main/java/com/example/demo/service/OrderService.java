package com.example.demo.service;

import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse createOrder(String username, OrderRequest request);

    Page<OrderResponse> getOrderHistory(String username, Pageable pageable);

    OrderResponse getOrderById(String username, Long orderId);
}
