package com.example.demo.service.impl;

import com.example.demo.dto.order.OrderItemRequest;
import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.entity.*;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EventPublisherService;
import com.example.demo.service.cache.ProductCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderServiceImpl.createOrder — the core checkout business logic.
 * Everything except the class under test is mocked, so these run fast with no DB/Redis/Kafka.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductCacheService productCacheService;
    @Mock private EventPublisherService eventPublisherService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("mohit")
                .email("mohit@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        product = Product.builder()
                .id(10L)
                .name("Wireless Mouse")
                .price(BigDecimal.valueOf(25.00))
                .stockQuantity(5)
                .build();
    }

    @Test
    void createOrder_success_decrementsStockAndPublishesEvents() {
        OrderItemRequest itemReq = new OrderItemRequest(10L, 2);
        OrderRequest request = new OrderRequest(List.of(itemReq));

        when(userRepository.findByUsername("mohit")).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        // orderRepository.save should return whatever Order it's given, with an id set
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrder("mohit", request);

        // stock decremented correctly: 5 - 2 = 3
        assertThat(product.getStockQuantity()).isEqualTo(3);

        // total = price * quantity = 25.00 * 2 = 50.00
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // cache invalidated for the product touched
        verify(productCacheService).invalidateProduct(10L);

        // both events fired exactly once
        verify(eventPublisherService, times(1)).publishStockUpdated(any());
        verify(eventPublisherService, times(1)).publishOrderCreated(any());

        // product persisted with new stock, order persisted
        verify(productRepository).save(product);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_insufficientStock_throwsBadRequestAndPublishesNoEvents() {
        product.setStockQuantity(1); // only 1 in stock
        OrderItemRequest itemReq = new OrderItemRequest(10L, 5); // requesting 5
        OrderRequest request = new OrderRequest(List.of(itemReq));

        when(userRepository.findByUsername("mohit")).thenReturn(Optional.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.createOrder("mohit", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");

        // nothing should have been saved or published once stock check fails
        verify(productRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(eventPublisherService, never()).publishStockUpdated(any());
        verify(eventPublisherService, never()).publishOrderCreated(any());
    }

    @Test
    void createOrder_productNotFound_throwsResourceNotFound() {
        OrderItemRequest itemReq = new OrderItemRequest(999L, 1);
        OrderRequest request = new OrderRequest(List.of(itemReq));

        when(userRepository.findByUsername("mohit")).thenReturn(Optional.of(user));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder("mohit", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found with id: 999");
    }

    @Test
    void createOrder_userNotFound_throwsResourceNotFound() {
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(10L, 1)));

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder("ghost", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verifyNoInteractions(productRepository, orderRepository, eventPublisherService);
    }
}