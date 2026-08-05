package com.example.demo.consumer;

import com.example.demo.event.OrderCreatedEvent;
import com.example.demo.event.StockUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventConsumer {

    @KafkaListener(topics = "${app.kafka.topic.order-created}", groupId = "demo-app")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Consumed OrderCreatedEvent: orderId={}, user={}, total={}",
                event.getOrderId(), event.getUsername(), event.getTotalAmount());
     }

    @KafkaListener(topics = "${app.kafka.topic.stock-updated}", groupId = "demo-app")
    public void handleStockUpdated(StockUpdatedEvent event) {
        log.info("Consumed StockUpdatedEvent: product={}, {} -> {}",
                event.getProductName(), event.getPreviousStock(), event.getNewStock());

    }
}