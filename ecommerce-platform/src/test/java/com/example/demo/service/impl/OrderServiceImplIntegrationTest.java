package com.example.demo.service.impl;

import com.example.demo.AbstractIntegrationTest;
import com.example.demo.dto.order.OrderItemRequest;
import com.example.demo.dto.order.OrderRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.entity.Category;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.Product;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.BadRequestException;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full integration test for OrderServiceImpl.createOrder — runs against REAL
 * Postgres, Redis, and Kafka containers (shared via AbstractIntegrationTest),
 * using the actual Spring context (repositories, cache service, Kafka producer
 * all wired for real).
 *
 * This is what proves the stack actually works end to end, not just that the
 * mocked calls were made (see OrderServiceImplTest for the fast unit-test version).
 *
 * Requires Docker running locally.
 */
class OrderServiceImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(
                Category.builder().name("Electronics-" + System.nanoTime()).build());

        product = productRepository.save(Product.builder()
                .name("Wireless Mouse")
                .price(BigDecimal.valueOf(25.00))
                .stockQuantity(5)
                .category(category)
                .build());

        user = userRepository.save(User.builder()
                .username("mohit-" + System.nanoTime())
                .email("mohit" + System.nanoTime() + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build());
    }

    @AfterEach
    void tearDown() {
        // keep DB clean between tests since we're reusing containers across the class
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createOrder_persistsToRealDbAndDecrementsStock() {
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(product.getId(), 2)));

        OrderResponse response = orderService.createOrder(user.getUsername(), request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));

        // re-fetch product from the real DB to confirm stock actually decremented
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void createOrder_insufficientStock_rollsBackAndThrows() {
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(product.getId(), 100)));

        assertThatThrownBy(() -> orderService.createOrder(user.getUsername(), request))
                .isInstanceOf(BadRequestException.class);

        // @Transactional on createOrder means stock must be unchanged after rollback
        Product unchanged = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void createOrder_publishesRealKafkaEvent() throws Exception {
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(product.getId(), 1)));

        OrderResponse response = orderService.createOrder(user.getUsername(), request);

        // consume directly from the real Kafka topic to prove the event was actually published
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList("order-created"));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThanOrEqualTo(1);
            boolean foundMatchingOrder = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(String.valueOf(response.getId()))) {
                    foundMatchingOrder = true;
                    assertThat(record.value()).contains(user.getUsername());
                }
            }
            assertThat(foundMatchingOrder).isTrue();
        }
    }
}
