package com.barry.saga.retail.orderservice.kafka.listener;

import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.orderservice.share.event.StockRejectedEvent;
import com.barry.saga.retail.orderservice.share.event.StockReservedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.barry.saga.retail.orderservice.entities.enums.OrderStatus.FAILED;
import static com.barry.saga.retail.orderservice.entities.enums.OrderStatus.STOCK_CHECKING;

@Component
@RequiredArgsConstructor
@Log4j2
public class StockEventListener {

    private final OrderRepository orderRepository;

    /**
     * Réception de l'évènement StockReservedEvent
     * -> le stock est réservé avec succès
     */

    @KafkaListener(
            topics = "stock.reserved",
            containerFactory = "stockReservedKafkaListenerContainerFactory"
    )
    @Transactional
    public void onStockReserved(StockReservedEvent stockReservedEvent, Acknowledgment ack) {
        log.info("Received StockReservedEvent for orderId= {}", stockReservedEvent.getOrderId());

        orderRepository.findById(stockReservedEvent.getOrderId()).ifPresent(order -> {
            order.setStatus(STOCK_CHECKING);
            orderRepository.save(order);

            // Commit Kafka APRES succès métier
            ack.acknowledge();
            log.info(" Order {} status updated to STOCK_CHECKING", order.getOrderId());
        });
    }

    /**
     * Stock insuffisant → échec de la commande
     */
    @KafkaListener(
            topics = "stock.rejected",
            containerFactory = "stockRejectedKafkaListenerContainerFactory"
    )
    @Transactional
    public void onStockRejected(StockRejectedEvent stockRejectedEvent, Acknowledgment ack) {
        log.warn("📤 Received StockRejectedEvent for orderId= {} | reason= {}",
                stockRejectedEvent.getOrderId(), stockRejectedEvent.getReason());

        orderRepository.findById(stockRejectedEvent.getOrderId()).ifPresent(order -> {
            order.setStatus(FAILED);
            orderRepository.save(order);

            // Commit Kafka APRES succès métier
            ack.acknowledge();
            log.warn("❌ Order {} status updated to FAILED", order.getOrderId());
        });
    }
}
