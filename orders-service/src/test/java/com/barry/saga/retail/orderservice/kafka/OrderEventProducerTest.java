package com.barry.saga.retail.orderservice.kafka;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate = mock(KafkaTemplate.class);
    private OrderEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KafkaTopicsConfig properties = new KafkaTopicsConfig();
        properties.getTopics().setOrderPlaced("order.placed");
        properties.getTopics().setOrderConfirmed("order.confirmed");
        properties.getTopics().setOrderFailed("order.failed");
        producer = new OrderEventProducer(kafkaTemplate, properties);

        // Le producteur enchaîne sur .whenComplete(...) : on rend un future déjà complété.
        SendResult<String, SpecificRecord> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Test
    @DisplayName("sendOrderPlaced publie sur le topic order.placed avec orderId en clé Kafka")
    void sendOrderPlaced_publishesToConfiguredTopic() {
        OrderPlacedEvent event = mock(OrderPlacedEvent.class);
        when(event.getOrderId()).thenReturn("order-1");

        producer.sendOrderPlaced(event);

        ProducerRecord<String, SpecificRecord> sentRecord = captureSentRecord();
        assertThat(sentRecord.topic()).isEqualTo("order.placed");
        assertThat(sentRecord.key()).isEqualTo("order-1");
        assertThat(sentRecord.value()).isSameAs(event);
    }

    @Test
    @DisplayName("sendOrderConfirmed publie sur le topic order.confirmed avec orderId en clé Kafka")
    void sendOrderConfirmed_publishesToConfiguredTopic() {
        OrderConfirmedEvent event = mock(OrderConfirmedEvent.class);
        when(event.getOrderId()).thenReturn("order-2");

        producer.sendOrderConfirmed(event);

        ProducerRecord<String, SpecificRecord> sentRecord = captureSentRecord();
        assertThat(sentRecord.topic()).isEqualTo("order.confirmed");
        assertThat(sentRecord.key()).isEqualTo("order-2");
        assertThat(sentRecord.value()).isSameAs(event);
    }

    @Test
    @DisplayName("sendOrderFailed publie sur le topic order.failed avec orderId en clé Kafka")
    void sendOrderFailed_publishesToConfiguredTopic() {
        OrderFailedEvent event = mock(OrderFailedEvent.class);
        when(event.getOrderId()).thenReturn("order-3");

        producer.sendOrderFailed(event);

        ProducerRecord<String, SpecificRecord> sentRecord = captureSentRecord();
        assertThat(sentRecord.topic()).isEqualTo("order.failed");
        assertThat(sentRecord.key()).isEqualTo("order-3");
        assertThat(sentRecord.value()).isSameAs(event);
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, SpecificRecord> captureSentRecord() {
        ArgumentCaptor<ProducerRecord<String, SpecificRecord>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }
}