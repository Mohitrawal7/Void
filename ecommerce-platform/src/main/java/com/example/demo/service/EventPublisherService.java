package com.example.demo.service;

import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.StockUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.order-created}")
    private String orderCreatedTopic;

    @Value("${app.kafka.topic.stock-updated}")
    private String stockUpdatedTopic;

    public void publishOrderCreated(OrderCreatedEvent event) {
        
        kafkaTemplate.send(orderCreatedTopic, String.valueOf(event.getOrderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}: {}", event.getOrderId(), ex.getMessage());
                    } else {
                        log.info("Published OrderCreatedEvent for order {}", event.getOrderId());
                    }
                });
    }

    public void publishStockUpdated(StockUpdatedEvent event) {
        kafkaTemplate.send(stockUpdatedTopic, String.valueOf(event.getProductId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish StockUpdatedEvent for product {}: {}", event.getProductId(), ex.getMessage());
                    } else {
                        log.info("Published StockUpdatedEvent for product {}", event.getProductId());
                    }
                });
    }
}