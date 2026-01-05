package com.barry.saga.retail.orderservice.kafka;

import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.orderservice.share.event.OrderConfirmedEvent;
import com.barry.saga.retail.orderservice.share.event.OrderFailedEvent;
import com.barry.saga.retail.orderservice.share.event.OrderPlacedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsConfig properties;

    /**
     * event événement contenant les informations d'une commande nouvellement créée
     */
    public void sendOrderPlaced(OrderPlacedEvent orderPlacedEvent) {
        String topic = properties.getTopics().getOrderPlaced();
        kafkaTemplate.send(topic, orderPlacedEvent);
        log.info("📤 OrderPlacedEvent send to kafka topic {}: ", topic);
    }

    /**
     * event événement déclenché après confirmation de la commande
     */
    public void sendOrderConfirmed(OrderConfirmedEvent orderConfirmedEvent) {
        kafkaTemplate.send(properties.getTopics().getOrderConfirmed(), orderConfirmedEvent);
        log.info("📤 OrderConfirmedEvent send to kafka topic {}: ", properties.getTopics().getOrderConfirmed());
    }

    public void sendOrderFailed(OrderFailedEvent orderFailedEvent) {
        kafkaTemplate.send(properties.getTopics().getOrderFailed(), orderFailedEvent);
        log.warn("📤 OrderFailedEvent send to kafka topic {}: ", properties.getTopics().getOrderFailed());
    }
}
