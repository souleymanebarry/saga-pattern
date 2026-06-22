package com.barry.saga.retail.orderservice.service.impl;

import com.barry.saga.retail.orderservice.client.catalog.CatalogClient;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.exceptions.OrderNotFoundException;
import com.barry.saga.retail.orderservice.kafka.OrderEventProducer;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.orderservice.service.OrderService;
import com.barry.saga.retail.order.event.OrderItem;
import com.barry.saga.retail.order.event.OrderPlacedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final KafkaTopicsConfig properties;
    private final CatalogClient catalogRestClient;

    // ----------------------------------------------------------------------
    //                MÉTHODE PRINCIPALE : CRÉATION DE COMMANDE
    // ----------------------------------------------------------------------

    /**
     * Crée une commande avec validation métier complète,
     * puis publie un événement OrderPlacedEvent dans Kafka.
     */

    @Transactional
    @Override
    public OrderEntity createOrder(OrderEntity orderEntity) {
        log.info("➡ Initialization of an order for customer {}", orderEntity.getCustomerId());

        // ensure idempotency key
        if (StringUtils.isBlank(orderEntity.getIdempotencyKey())) {
            orderEntity.setIdempotencyKey(UUID.randomUUID().toString());
        }

        // Idempotency check
        final String idempotencyKey = orderEntity.getIdempotencyKey();
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("♻ Commande déjà existante pour idempotencyKey={} → renvoi sans création",
                    orderEntity.getIdempotencyKey());
            return existing.get();
        }

        // 1 Basic validation (structure + quantités) AVANT tout appel externe :
        //   évite un NPE si items == null et n'appelle pas le catalog pour rien.
        validateOrderItems(orderEntity);

        // 2 Enrich items from catalog-service (SOURCE OF TRUTH)
        enrichOrderItemsWithCatalogData(orderEntity);

        // 3 Initialisation de la commande
        // -----------------------------
        orderEntity.setStatus(OrderStatus.PENDING);
        orderEntity.setCreatedAt(LocalDateTime.now());
        orderEntity.setUpdatedAt(LocalDateTime.now());

        // fetch real sku  Binding item → ordre
        orderEntity.getItems().forEach(item -> item.setOrder(orderEntity));

        // compute total
        orderEntity.setTotalAmount(getTotalAmount(orderEntity));

        // 4 Persist
        OrderEntity savedOrder = orderRepository.save(orderEntity);

        log.info("✔ Commande {} créée avec succès", savedOrder.getOrderId());

        // Event Kafka
        orderEventProducer.sendOrderPlaced(toOrderPlacedEvent(savedOrder));
        log.info("📡 OrderPlacedEvent Send to Kafka for orderId: {} to  topic: '{}'",
                savedOrder.getOrderId(), properties.getTopics().getOrderPlaced());

        return savedOrder;
    }

    // ----------------------------------------------------------------------
    //                   LECTURE D’UNE COMMANDES PAR ID
    // ----------------------------------------------------------------------

    public OrderEntity getOrderByOrderId(UUID orderId) {
        log.info("🔎 Searching for order with ID: {}", orderId);
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.error("❌ Order not found for ID: {}", orderId);
                    return new OrderNotFoundException(orderId.toString());
                });
    }

    // ----------------------------------------------------------------------
    //                   CONSTRUCTION MANUELLE DES EVENTS
    // ----------------------------------------------------------------------

    public OrderPlacedEvent toOrderPlacedEvent(OrderEntity orderEntity) {
        return OrderPlacedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setIdempotencyKey(orderEntity.getIdempotencyKey())
                .setOrderId(orderEntity.getOrderId().toString())
                .setCustomerId(orderEntity.getCustomerId())
                .setTotalAmount(toMoney(orderEntity.getTotalAmount()))
                .setCreatedAt(orderEntity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .setItems(
                        orderEntity.getItems().stream()
                                .map(this::toOrderItemEvent)
                                .toList())
                .build();
    }

    private OrderItem toOrderItemEvent(OrderItemEntity item) {
        return OrderItem.newBuilder()
                .setSku(item.getSku())
                .setQuantity(item.getQuantity())
                .setUnitPrice(toMoney(item.getUnitPrice()))
                .build();
    }

    /**
     * Le type Avro {@code decimal(scale=2)} exige une échelle exacte de 2 ;
     * on normalise donc tout montant monétaire avant sérialisation.
     */
    private BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // -----------------------------
    // Règles métier pour une création de commande.
    // -----------------------------

    private void validateOrderItems(OrderEntity orderEntity) {
        if (orderEntity.getItems() == null || orderEntity.getItems().isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }

        // Vérifier chaque ligne
        for (OrderItemEntity item : orderEntity.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("La quantité doit être > 0 pour l'article "+ item.getSku());
            }
        }
    }

    private BigDecimal getTotalAmount(OrderEntity orderEntity) {
        return orderEntity.getItems().stream().map(item ->
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    // ----------------------------------------------------------------------
    //      ENRICH ORDER WITH CATALOG DATA (REST CALL TO CATALOG SERVICE)
    // ----------------------------------------------------------------------

    private void enrichOrderItemsWithCatalogData(OrderEntity orderEntity) {
        orderEntity.getItems().forEach(item->  {
            // call catalog-service API
            var product = catalogRestClient.getProductBySku(item.getSku());

            if (product == null) {
                throw new IllegalStateException("Product with SKU "+item.getSku()+"does not exist in catalog-service");
            }

            //Enrichment from catalog (SOURCE OF TRUTH)
            item.setSku(product.getSku());  // same SKU, validated
            item.setUnitPrice(product.getPrice());
            item.setProductName(product.getName());
        });
    }

}
