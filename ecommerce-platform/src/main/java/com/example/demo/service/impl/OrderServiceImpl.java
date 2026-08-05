package com.example.demo.service.impl;

import com.example.demo.dto.order.OrderItemRequest;
import com.example.demo.dto.order.OrderItemResponse;
import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.entity.*;
import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.StockUpdatedEvent;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EventPublisherService;
import com.example.demo.service.OrderService;
import com.example.demo.service.cache.ProductCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductCacheService productCacheService;
    private final EventPublisherService eventPublisherService;

    @Override
    @Transactional
    public OrderResponse createOrder(String username, OrderRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new BadRequestException("Insufficient stock for product: " + product.getName());
            }

            // decrement stock
            int previousStock = product.getStockQuantity();
            product.setStockQuantity(previousStock - itemReq.getQuantity());
            productRepository.save(product);
            productCacheService.invalidateProduct(product.getId());   // <-- add this line

            //event publish
            eventPublisherService.publishStockUpdated(
                    StockUpdatedEvent.builder()
                            .productId(product.getId())
                            .productName(product.getName())
                            .previousStock(previousStock)
                            .newStock(product.getStockQuantity())
                            .reason("ORDER_PLACED")
                            .build()
            );

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .price(product.getPrice())
                    .build();

            order.addItem(orderItem);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        order.setTotalAmount(total);
        order.setStatus(OrderStatus.CONFIRMED);

        Order saved = orderRepository.save(order);

        eventPublisherService.publishOrderCreated(
                OrderCreatedEvent.builder()
                        .orderId(saved.getId())
                        .username(username)
                        .totalAmount(saved.getTotalAmount())
                        .orderDate(saved.getOrderDate())
                        .items(saved.getOrderItems().stream()
                                .map(i -> OrderCreatedEvent.OrderItemEvent.builder()
                                        .productId(i.getProduct().getId())
                                        .productName(i.getProduct().getName())
                                        .quantity(i.getQuantity())
                                        .price(i.getPrice())
                                        .build())
                                .toList())
                        .build()
        );

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrderHistory(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return orderRepository.findByUserOrderByOrderDateDesc(user, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String username, Long orderId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .orderDate(order.getOrderDate())
                .items(items)
                .build();
    }
}
