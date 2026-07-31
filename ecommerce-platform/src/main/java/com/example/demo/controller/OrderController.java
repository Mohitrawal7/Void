package com.example.demo.controller;

import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request,
                                                       Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(username, request));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<OrderResponse>> getOrderHistory(Authentication authentication,
                                                                 @PageableDefault(size = 10) Pageable pageable) {
        String username = authentication.getName();
        return ResponseEntity.ok(orderService.getOrderHistory(username, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(orderService.getOrderById(username, id));
    }
}
