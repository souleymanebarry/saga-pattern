package com.barry.saga.retail.orderservice.kafka.listener;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.kafka.OrderEventProducer;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.stock.event.StockRejectedEvent;
import com.barry.saga.retail.stock.event.StockReservedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.barry.saga.retail.orderservice.entities.enums.OrderStatus.CONFIRMED;
import static com.barry.saga.retail.orderservice.entities.enums.OrderStatus.FAILED;
import static com.barry.saga.retail.orderservice.entities.enums.OrderStatus.STOCK_RESERVED;

@Component
@RequiredArgsConstructor
@Log4j2
public class StockEventListener {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    /**
     * Réception de l'évènement StockReservedEvent.
     * Le stock est réservé avec succès : on acte d'abord la réservation
     * (STOCK_RESERVED), puis — en l'absence d'étape paiement — on confirme
     * la commande (CONFIRMED) et on publie un OrderConfirmedEvent.
     */
    @KafkaListener(topics = "${spring.kafka.topics.stock-reserved}")
    @Transactional
    public void onStockReserved(StockReservedEvent event, Acknowledgment ack) {
        UUID orderId = UUID.fromString(event.getOrderId());
        log.info("📥 Received StockReservedEvent for orderId={}", orderId);

        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            // 1. Réservation actée
            order.setStatus(STOCK_RESERVED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("✅ Order {} status updated to STOCK_RESERVED", orderId);

            // 2. Confirmation (réservation = succès final du Saga ici)
            order.setStatus(CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("✅ Order {} status updated to CONFIRMED", orderId);

            orderEventProducer.sendOrderConfirmed(toOrderConfirmedEvent(order));
            log.info("📡 OrderConfirmedEvent published for orderId={}", orderId);
        }, () -> log.warn("⚠ Order {} not found for StockReservedEvent — skipping", orderId));

        // Commit Kafka systématique : même si la commande est introuvable,
        // rejouer l'événement n'y changerait rien (évite le poison pill).
        ack.acknowledge();
    }

    /**
     * Stock insuffisant → échec de la commande : statut FAILED et publication
     * d'un OrderFailedEvent (compensation / notification aval).
     */
    @KafkaListener(topics = "${spring.kafka.topics.stock-rejected}")
    @Transactional
    public void onStockRejected(StockRejectedEvent event, Acknowledgment ack) {
        UUID orderId = UUID.fromString(event.getOrderId());
        log.warn("📥 Received StockRejectedEvent for orderId={} | reason={}",
                orderId, event.getReason());

        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.setStatus(FAILED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.warn("❌ Order {} status updated to FAILED", orderId);

            orderEventProducer.sendOrderFailed(toOrderFailedEvent(order, event.getReason().toString()));
            log.info("📡 OrderFailedEvent published for orderId={}", orderId);
        }, () -> log.warn("⚠ Order {} not found for StockRejectedEvent — skipping", orderId));

        ack.acknowledge();
    }

    // ----------------------------------------------------------------------
    //                   CONSTRUCTION DES EVENTS DE SORTIE
    // ----------------------------------------------------------------------

    private OrderConfirmedEvent toOrderConfirmedEvent(OrderEntity order) {
        return OrderConfirmedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(order.getOrderId().toString())
                .setCustomerId(order.getCustomerId())
                .setTotalAmount(toMoney(order.getTotalAmount()))
                .setConfirmedAt(Instant.now())
                .build();
    }

    private OrderFailedEvent toOrderFailedEvent(OrderEntity order, String reason) {
        return OrderFailedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(order.getOrderId().toString())
                .setCustomerId(order.getCustomerId())
                .setReason(reason)
                .setFailedAt(Instant.now())
                .build();
    }

    /**
     * Le type Avro {@code decimal(scale=2)} exige une échelle exacte de 2.
     */
    private BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}