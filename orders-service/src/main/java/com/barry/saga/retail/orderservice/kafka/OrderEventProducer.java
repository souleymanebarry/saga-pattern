package com.barry.saga.retail.orderservice.kafka;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Log4j2
public class OrderEventProducer {

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final KafkaTopicsConfig properties;

    /**
     * Publie l'événement Avro d'une commande nouvellement créée.
     * La clé Kafka = orderId garantit l'ordre des événements d'une même commande
     * (même partition), condition essentielle au Saga.
     */
    public void sendOrderPlaced(OrderPlacedEvent event) {
        send(properties.getTopics().getOrderPlaced(), event.getOrderId(), event);
    }

    /**
     * Publie l'événement de confirmation d'une commande (fin nominale du Saga).
     */
    public void sendOrderConfirmed(OrderConfirmedEvent event) {
        send(properties.getTopics().getOrderConfirmed(), event.getOrderId(), event);
    }

    /**
     * Publie l'événement d'échec d'une commande (stock rejeté / compensation Saga).
     */
    public void sendOrderFailed(OrderFailedEvent event) {
        send(properties.getTopics().getOrderFailed(), event.getOrderId(), event);
    }

    // ===============================
    // Méthode centrale d’envoi Kafka
    // ===============================
    private void send(String topic, String key, SpecificRecord event) {

        ProducerRecord<String, SpecificRecord> record =
                new ProducerRecord<>(topic, key, event);

        // Headers standards Saga / Event-driven
        record.headers().add("eventType", event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "v1".getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Failed to send event {} to topic {}",
                                event.getClass().getSimpleName(), topic, ex);
                    } else {
                        log.info("📤 {} sent to topic {} (key={}, partition={}, offset={})",
                                event.getClass().getSimpleName(),
                                topic,
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}