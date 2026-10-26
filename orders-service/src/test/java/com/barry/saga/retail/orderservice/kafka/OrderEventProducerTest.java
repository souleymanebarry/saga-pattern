package com.barry.saga.retail.orderservice.kafka;

import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventProducerTest {

    private static final String ORDER_ID = "order-1";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate = mock(KafkaTemplate.class);
    private OrderEventProducer producer;

    @BeforeEach
    void setUp() {
        KafkaTopicsConfig properties = new KafkaTopicsConfig();
        properties.getTopics().setOrderPlaced("order.placed");
        properties.getTopics().setOrderConfirmed("order.confirmed");
        properties.getTopics().setOrderFailed("order.failed");
        producer = new OrderEventProducer(kafkaTemplate, properties);

        // Future jamais complétée : le callback whenComplete est simplement enregistré
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
    }

    @Test
    @DisplayName("sendOrderPlaced publie l'évènement sur le topic order.placed configuré")
    void sendOrderPlaced_publishesToConfiguredTopic() {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(ORDER_ID);

        producer.sendOrderPlaced(event);

        assertPublished("order.placed", event);
    }

    @Test
    @DisplayName("sendOrderConfirmed publie l'évènement sur le topic order.confirmed configuré")
    void sendOrderConfirmed_publishesToConfiguredTopic() {
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        event.setOrderId(ORDER_ID);

        producer.sendOrderConfirmed(event);

        assertPublished("order.confirmed", event);
    }

    @Test
    @DisplayName("sendOrderFailed publie l'évènement sur le topic order.failed configuré")
    void sendOrderFailed_publishesToConfiguredTopic() {
        OrderFailedEvent event = new OrderFailedEvent();
        event.setOrderId(ORDER_ID);

        producer.sendOrderFailed(event);

        assertPublished("order.failed", event);
    }

    /**
     * Vérifie topic, clé (orderId, pour l'ordre par partition), valeur et headers Saga.
     */
    @SuppressWarnings("unchecked")
    private void assertPublished(String topic, SpecificRecord event) {
        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, SpecificRecord> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(topic);
        assertThat(record.key()).isEqualTo(ORDER_ID);
        assertThat(record.value()).isSameAs(event);
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getClass().getSimpleName());
        assertThat(new String(record.headers().lastHeader("schemaVersion").value(), StandardCharsets.UTF_8))
                .isEqualTo("v1");
    }
}
