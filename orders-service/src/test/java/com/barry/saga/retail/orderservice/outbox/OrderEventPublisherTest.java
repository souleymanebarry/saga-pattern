package com.barry.saga.retail.orderservice.outbox;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderEventPublisherTest {

    private static final String ORDER_ID = "order-1";
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final byte[] PAYLOAD = {0, 0, 0, 0, 1, 42};

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final KafkaAvroSerializer avroSerializer = mock(KafkaAvroSerializer.class);
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        KafkaTopicsConfig properties = new KafkaTopicsConfig();
        properties.getTopics().setOrderPlaced("order.placed");
        properties.getTopics().setOrderConfirmed("order.confirmed");
        properties.getTopics().setOrderFailed("order.failed");
        publisher = new OrderEventPublisher(outboxEventRepository, avroSerializer, properties);

        when(avroSerializer.serialize(anyString(), any())).thenReturn(PAYLOAD);
    }

    @Test
    @DisplayName("publishOrderPlaced écrit l'évènement dans l'outbox pour le topic order.placed")
    void publishOrderPlaced_writesOutboxRow() {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setEventId(EVENT_ID);
        event.setOrderId(ORDER_ID);

        publisher.publishOrderPlaced(event);

        assertStored("order.placed", event);
    }

    @Test
    @DisplayName("publishOrderConfirmed écrit l'évènement dans l'outbox pour le topic order.confirmed")
    void publishOrderConfirmed_writesOutboxRow() {
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        event.setEventId(EVENT_ID);
        event.setOrderId(ORDER_ID);

        publisher.publishOrderConfirmed(event);

        assertStored("order.confirmed", event);
    }

    @Test
    @DisplayName("publishOrderFailed écrit l'évènement dans l'outbox pour le topic order.failed")
    void publishOrderFailed_writesOutboxRow() {
        OrderFailedEvent event = new OrderFailedEvent();
        event.setEventId(EVENT_ID);
        event.setOrderId(ORDER_ID);

        publisher.publishOrderFailed(event);

        assertStored("order.failed", event);
    }

    /**
     * Vérifie la sérialisation Avro pour le bon topic et le contenu de la ligne outbox :
     * id = eventId, clé = orderId (ordre par partition), type et version de l'évènement.
     */
    private void assertStored(String topic, SpecificRecord event) {
        verify(avroSerializer).serialize(topic, event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEventEntity row = captor.getValue();
        assertThat(row.getId()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(row.getAggregateType()).isEqualTo("Order");
        assertThat(row.getAggregateId()).isEqualTo(ORDER_ID);
        assertThat(row.getTopic()).isEqualTo(topic);
        assertThat(row.getEventType()).isEqualTo(event.getClass().getSimpleName());
        assertThat(row.getSchemaVersion()).isEqualTo("v1");
        assertThat(row.getPayload()).isEqualTo(PAYLOAD);
        assertThat(row.getCreatedAt()).isNotNull();
    }
}
