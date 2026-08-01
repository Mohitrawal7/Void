package com.example.demo.service.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProductCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIST_PREFIX = "products:list:";
    private static final String ITEM_PREFIX = "products:item:";

  public String buildListKey() {
    return LIST_PREFIX + "all";
}

    public String buildItemKey(Long productId) {
        return ITEM_PREFIX + productId;
    }

    @SuppressWarnings("unchecked")
    public <T> T getList(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public void putList(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(ttlSeconds));
    }

    @SuppressWarnings("unchecked")
    public <T> T getItem(Long productId) {
        return (T) redisTemplate.opsForValue().get(buildItemKey(productId));
    }

    public void putItem(Long productId, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(buildItemKey(productId), value, java.time.Duration.ofSeconds(ttlSeconds));
    }

    /** Invalidate every cached listing variation + a single product item. Call on any create/update/delete/stock change. */
    public void invalidateProduct(Long productId) {
        Set<String> listKeys = redisTemplate.keys(LIST_PREFIX + "*");
        if (listKeys != null && !listKeys.isEmpty()) {
            redisTemplate.delete(listKeys);
        }
        if (productId != null) {
            redisTemplate.delete(buildItemKey(productId));
        }
    }
}