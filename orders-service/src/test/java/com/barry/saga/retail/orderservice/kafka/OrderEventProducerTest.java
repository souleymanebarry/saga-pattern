package com.barry.saga.retail.orderservice.kafka;

import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.orderservice.share.event.OrderConfirmedEvent;
import com.barry.saga.retail.orderservice.share.event.OrderFailedEvent;
import com.barry.saga.retail.orderservice.share.event.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private OrderEventProducer producer;

    @BeforeEach
    void setUp() {
        KafkaTopicsConfig properties = new KafkaTopicsConfig();
        properties.getTopics().setOrderPlaced("order.placed");
        properties.getTopics().setOrderConfirmed("order.confirmed");
        properties.getTopics().setOrderFailed("order.failed");
        producer = new OrderEventProducer(kafkaTemplate, properties);
    }

    @Test
    @DisplayName("sendOrderPlaced publie l'évènement sur le topic order.placed configuré")
    void sendOrderPlaced_publishesToConfiguredTopic() {
        OrderPlacedEvent event = OrderPlacedEvent.builder().build();

        producer.sendOrderPlaced(event);

        verify(kafkaTemplate).send("order.placed", event);
    }

    @Test
    @DisplayName("sendOrderConfirmed publie l'évènement sur le topic order.confirmed configuré")
    void sendOrderConfirmed_publishesToConfiguredTopic() {
        OrderConfirmedEvent event = new OrderConfirmedEvent();

        producer.sendOrderConfirmed(event);

        verify(kafkaTemplate).send("order.confirmed", event);
    }

    @Test
    @DisplayName("sendOrderFailed publie l'évènement sur le topic order.failed configuré")
    void sendOrderFailed_publishesToConfiguredTopic() {
        OrderFailedEvent event = new OrderFailedEvent();

        producer.sendOrderFailed(event);

        verify(kafkaTemplate).send("order.failed", event);
    }
}