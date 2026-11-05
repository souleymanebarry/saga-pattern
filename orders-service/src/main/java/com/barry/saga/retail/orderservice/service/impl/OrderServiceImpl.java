package com.barry.saga.retail.orderservice.service.impl;

import com.barry.saga.retail.orderservice.client.catalog.CatalogClient;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.exceptions.IdempotencyKeyMismatchException;
import com.barry.saga.retail.orderservice.exceptions.InvalidOrderException;
import com.barry.saga.retail.orderservice.exceptions.MissingIdempotencyKeyException;
import com.barry.saga.retail.orderservice.exceptions.OrderNotFoundException;
import com.barry.saga.retail.orderservice.exceptions.ProductNotFoundException;
import com.barry.saga.retail.orderservice.outbox.OrderEventPublisher;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.orderservice.service.OrderService;
import com.barry.saga.retail.order.event.OrderItem;
import com.barry.saga.retail.order.event.OrderPlacedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class OrderServiceImpl implements OrderService {

    /** Contrainte UNIQUE sur orders.idempotency_key (changeset 001-create-orders-table). */
    static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_orders_idempotency_key";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final KafkaTopicsConfig properties;
    private final CatalogClient catalogRestClient;
    private final TransactionTemplate transactionTemplate;

    // ----------------------------------------------------------------------
    //                MÉTHODE PRINCIPALE : CRÉATION DE COMMANDE
    // ----------------------------------------------------------------------

    /**
     * Crée une commande avec validation métier complète,
     * puis enregistre un événement OrderPlacedEvent dans l'outbox (publié sur Kafka par Debezium).
     * <p>
     * Requêtes simultanées avec la même clé : toutes passent la vérification d'idempotence,
     * la contrainte {@value #IDEMPOTENCY_KEY_CONSTRAINT} n'en laisse insérer qu'une, et les autres
     * renvoient la commande qu'elle a créée. La transaction est gérée par {@link TransactionTemplate}
     * (et non {@code @Transactional}) pour intercepter l'échec <b>après</b> son rollback :
     * Postgres refuse toute requête dans une transaction où un INSERT a échoué.
     * <p>
     * Une clé connue n'est rejouée que si le contenu de la requête est le même : sinon le client
     * réutilise une clé pour une autre commande, et on refuse ({@link IdempotencyKeyMismatchException}).
     */
    @Override
    public OrderEntity createOrder(OrderEntity orderEntity) {
        log.info("➡ Initialization of an order for customer {}", orderEntity.getCustomerId());

        // La clé vient du client : en générer une ici rendrait chaque nouvel essai unique
        if (StringUtils.isBlank(orderEntity.getIdempotencyKey())) {
            throw new MissingIdempotencyKeyException();
        }

        final String idempotencyKey = orderEntity.getIdempotencyKey();
        // Calculée avant l'enrichissement catalogue : seul compte ce que le client a envoyé
        orderEntity.setRequestHash(computeRequestHash(orderEntity));
        try {
            return transactionTemplate.execute(status -> createOrderInTransaction(orderEntity));
        } catch (DataIntegrityViolationException ex) {
            if (!isIdempotencyKeyViolation(ex)) {
                throw ex;
            }
            log.info("♻ Concurrent request already created the order for idempotencyKey={} → returning it",
                    idempotencyKey);
            OrderEntity concurrent = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
            return ensureSameRequest(concurrent, orderEntity);
        }
    }

    private OrderEntity createOrderInTransaction(OrderEntity orderEntity) {
        // Idempotency check
        final String idempotencyKey = orderEntity.getIdempotencyKey();
        var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("♻ Order already exists for idempotencyKey={} → " +
                            "returning existing order without creating a new one",
                    orderEntity.getIdempotencyKey());
            return ensureSameRequest(existing.get(), orderEntity);
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

        // 4 Persist (flush immédiat : un doublon de clé échoue ici, avant l'écriture dans l'outbox)
        OrderEntity savedOrder = orderRepository.saveAndFlush(orderEntity);

        log.info("✔ Order {} created successfully", savedOrder.getOrderId());

        // 5 Évènement écrit dans l'outbox, dans la même transaction que la commande
        //   (Debezium le publiera ensuite sur Kafka)
        orderEventPublisher.publishOrderPlaced(toOrderPlacedEvent(savedOrder));
        log.info("📡 OrderPlacedEvent stored in outbox for orderId: {} (topic: '{}')",
                savedOrder.getOrderId(), properties.getTopics().getOrderPlaced());

        return savedOrder;
    }

    /**
     * Rejeu d'une clé connue : renvoie la commande existante si la requête est la même.
     * Une commande antérieure à la colonne request_hash (NULL) est renvoyée sans comparaison.
     */
    private OrderEntity ensureSameRequest(OrderEntity existing, OrderEntity incoming) {
        if (existing.getRequestHash() != null && !existing.getRequestHash().equals(incoming.getRequestHash())) {
            log.warn("❌ idempotencyKey={} already used for order {} with a different request",
                    incoming.getIdempotencyKey(), existing.getOrderId());
            throw new IdempotencyKeyMismatchException(incoming.getIdempotencyKey());
        }
        return existing;
    }

    /**
     * Empreinte SHA-256 du contenu métier de la requête : client + articles (SKU, quantité).
     * Les articles sont triés par SKU : le même panier dans un autre ordre est la même commande.
     */
    static String computeRequestHash(OrderEntity order) {
        List<OrderItemEntity> items = Objects.requireNonNullElse(order.getItems(), List.of());
        String canonical = order.getCustomerId() + "|" + items.stream()
                .map(item -> item.getSku() + ":" + item.getQuantity())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean isIdempotencyKeyViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return IDEMPOTENCY_KEY_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
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
            throw new InvalidOrderException("An order must contain at least one item");
        }

        // Vérifier chaque ligne
        for (OrderItemEntity item : orderEntity.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InvalidOrderException("Quantity must be greater than 0 for item " + item.getSku());
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
                throw new ProductNotFoundException(item.getSku());
            }

            //Enrichment from catalog (SOURCE OF TRUTH)
            item.setSku(product.getSku());  // same SKU, validated
            item.setUnitPrice(product.getPrice());
            item.setProductName(product.getName());
        });
    }

}
