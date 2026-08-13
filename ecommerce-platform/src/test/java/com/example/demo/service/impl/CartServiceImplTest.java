package com.example.demo.service.impl;

import com.example.demo.dto.cart.CartItemRequest;
import com.example.demo.dto.cart.CartResponse;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.Product;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for CartServiceImpl — the cart-lives-entirely-in-Redis logic.
 * RedisTemplate/HashOperations are mocked, so this never touches a real Redis
 * instance; it only verifies the correct Redis commands get called with the
 * correct keys/values (see ProductServiceImplIntegrationTest-style tests for
 * proof that real Redis serialization works).
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private ProductRepository productRepository;
    @Mock private OrderService orderService;

    @InjectMocks
    private CartServiceImpl cartService;

    private static final String USERNAME = "mohit";
    private static final String CART_KEY = "cart:mohit";

    private Product product;

    @BeforeEach
    void setUp() {
        // cartTtlSeconds is set via @Value, not through the constructor, so inject it directly
        ReflectionTestUtils.setField(cartService, "cartTtlSeconds", 86400L);

        // every hashOps() call inside CartServiceImpl goes through redisTemplate.opsForHash()
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        product = Product.builder()
                .id(10L)
                .name("Wireless Mouse")
                .price(BigDecimal.valueOf(25.00))
                .stockQuantity(5)
                .build();
    }

    @Test
    void addItem_success_incrementsHashAndRefreshesTtl() {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(10L);
        request.setQuantity(2);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        // getCart() is called internally after adding — return a hash with the item already there
        Map<Object, Object> raw = new HashMap<>();
        raw.put("10", 2);
        when(hashOperations.entries(CART_KEY)).thenReturn(raw);

        CartResponse response = cartService.addItem(USERNAME, request);

        verify(hashOperations).increment(CART_KEY, "10", 2);
        verify(redisTemplate).expire(CART_KEY, Duration.ofSeconds(86400L));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(10L);
        assertThat(response.getItems().get(0).getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
    }

    @Test
    void addItem_insufficientStock_throwsAndNeverTouchesRedis() {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(10L);
        request.setQuantity(999);

        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(USERNAME, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");

        verify(hashOperations, never()).increment(anyString(), any(), anyLong());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void addItem_productNotFound_throwsResourceNotFound() {
        CartItemRequest request = new CartItemRequest();
        request.setProductId(999L);
        request.setQuantity(1);

        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USERNAME, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemQuantity_positiveQuantity_putsInHash() {
        Map<Object, Object> raw = new HashMap<>();
        raw.put("10", 3);
        when(hashOperations.entries(CART_KEY)).thenReturn(raw);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        cartService.updateItemQuantity(USERNAME, 10L, 3);

        verify(hashOperations).put(CART_KEY, "10", 3);
        verify(hashOperations, never()).delete(anyString(), any());
        verify(redisTemplate).expire(CART_KEY, Duration.ofSeconds(86400L));
    }

    @Test
    void updateItemQuantity_zeroOrNegative_removesFromHash() {
        when(hashOperations.entries(CART_KEY)).thenReturn(new HashMap<>());

        cartService.updateItemQuantity(USERNAME, 10L, 0);

        verify(hashOperations).delete(CART_KEY, "10");
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    void removeItem_deletesFromHash() {
        when(hashOperations.entries(CART_KEY)).thenReturn(new HashMap<>());

        cartService.removeItem(USERNAME, 10L);

        verify(hashOperations).delete(CART_KEY, "10");
    }

    @Test
    void clearCart_deletesEntireKey() {
        cartService.clearCart(USERNAME);

        verify(redisTemplate).delete(CART_KEY);
    }

    @Test
    void getCart_skipsProductsThatWereDeletedSinceAddedToCart() {
        Map<Object, Object> raw = new HashMap<>();
        raw.put("10", 2);   // still exists
        raw.put("999", 1);  // deleted since being added

        when(hashOperations.entries(CART_KEY)).thenReturn(raw);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(USERNAME);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(10L);
    }

    @Test
    void checkout_emptyCart_throwsBadRequest() {
        when(hashOperations.entries(CART_KEY)).thenReturn(new HashMap<>());

        assertThatThrownBy(() -> cartService.checkout(USERNAME))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cart is empty");

        verifyNoInteractions(orderService);
    }

    @Test
    void checkout_success_placesOrderThenClearsCart() {
        Map<Object, Object> raw = new HashMap<>();
        raw.put("10", 2);
        when(hashOperations.entries(CART_KEY)).thenReturn(raw);

        OrderResponse orderResponse = OrderResponse.builder()
                .id(100L)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(50.00))
                .build();
        when(orderService.createOrder(eq(USERNAME), any())).thenReturn(orderResponse);

        OrderResponse response = cartService.checkout(USERNAME);

        assertThat(response.getId()).isEqualTo(100L);
        verify(orderService).createOrder(eq(USERNAME), any());
        // cart must be cleared only AFTER the order succeeds
        verify(redisTemplate).delete(CART_KEY);
    }
}