package com.barry.saga.retail.orderservice.outbox;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publication des évènements "order" via le pattern Transactional Outbox.
 * Aucun appel Kafka ici : l'évènement est sérialisé en Avro puis inséré dans
 * la table outbox_event, dans la transaction de l'appelant (MANDATORY). Soit
 * la commande ET l'évènement sont commités, soit aucun des deux. Debezium lit
 * ensuite le WAL Postgres et publie le message sur Kafka.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class OrderEventPublisher {

    static final String AGGREGATE_TYPE = "Order";
    static final String SCHEMA_VERSION = "v1";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaAvroSerializer avroSerializer;
    private final KafkaTopicsConfig properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderPlaced(OrderPlacedEvent event) {
        save(properties.getTopics().getOrderPlaced(), event.getEventId(), event.getOrderId(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        save(properties.getTopics().getOrderConfirmed(), event.getEventId(), event.getOrderId(), event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderFailed(OrderFailedEvent event) {
        save(properties.getTopics().getOrderFailed(), event.getEventId(), event.getOrderId(), event);
    }

    private void save(String topic, String eventId, String orderId, SpecificRecord event) {
        String eventType = event.getClass().getSimpleName();

        // Sérialisation identique à l'ancien KafkaTemplate (subject = <topic>-value) :
        // les consommateurs (KafkaAvroDeserializer) ne voient aucune différence.
        byte[] payload = avroSerializer.serialize(topic, event);

        outboxEventRepository.save(OutboxEventEntity.builder()
                .id(UUID.fromString(eventId))
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(orderId)
                .topic(topic)
                .eventType(eventType)
                .schemaVersion(SCHEMA_VERSION)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("📝 {} written to outbox (topic={}, key={})", eventType, topic, orderId);
    }
}
