package com.barry.saga.retail.orderservice.service.impl;

import com.barry.saga.retail.orderservice.client.catalog.CatalogClient;
import com.barry.saga.retail.orderservice.client.dto.ProductResponseDTO;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.exceptions.OrderNotFoundException;
import com.barry.saga.retail.orderservice.kafka.OrderEventProducer;
import com.barry.saga.retail.orderservice.kafka.config.KafkaTopicsConfig;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.order.event.OrderPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private static final String SKU_1 = "SKU-1";
    private static final String SKU_2 = "SKU-2";
    private static final String CUSTOMER_ID = "cust-1";
    private static final String PRODUCT_NAME = "Keyboard";
    private static final BigDecimal PRICE_TEN = new BigDecimal("10.00");

    private OrderRepository orderRepository;
    private OrderEventProducer orderEventProducer;
    private CatalogClient catalogClient;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderEventProducer = mock(OrderEventProducer.class);
        catalogClient = mock(CatalogClient.class);

        // real config to avoid NPE on logging properties.getTopics().getOrderPlaced()
        KafkaTopicsConfig properties = new KafkaTopicsConfig();
        properties.getTopics().setOrderPlaced("order.placed");

        orderService = new OrderServiceImpl(orderRepository, orderEventProducer, properties, catalogClient);
    }

    // ----------------------------------------------------------------------
    //                          createOrder
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("createOrder génère une idempotencyKey quand elle est absente")
    void createOrder_generatesIdempotencyKey_whenBlank() {
        OrderEntity order = orderWithItem(SKU_1, 2, null);
        stubCatalog(SKU_1, PRODUCT_NAME, new BigDecimal("19.99"));
        givenNoExistingOrderAndSaveEchoesBack();

        OrderEntity saved = orderService.createOrder(order);

        assertThat(saved.getIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("createOrder renvoie la commande existante sans recréer ni publier d'évènement (idempotence)")
    void createOrder_returnsExisting_whenIdempotencyKeyExists() {
        OrderEntity incoming = orderWithItem(SKU_1, 1, null);
        incoming.setIdempotencyKey("key-123");
        OrderEntity existing = orderWithItem(SKU_1, 1, PRICE_TEN);
        existing.setIdempotencyKey("key-123");
        when(orderRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));

        OrderEntity result = orderService.createOrder(incoming);

        assertThat(result).isSameAs(existing);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventProducer, catalogClient);
    }

    @Test
    @DisplayName("createOrder enrichit les articles avec les données du catalogue (source de vérité)")
    void createOrder_enrichesItemsFromCatalog() {
        OrderEntity order = orderWithItem(SKU_1, 3, new BigDecimal("999.99")); // prix client ignoré
        stubCatalog(SKU_1, "Mouse", new BigDecimal("25.00"));
        givenNoExistingOrderAndSaveEchoesBack();

        OrderEntity saved = orderService.createOrder(order);

        OrderItemEntity item = saved.getItems().get(0);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("25.00");
        assertThat(item.getProductName()).isEqualTo("Mouse");
    }

    @Test
    @DisplayName("createOrder calcule le montant total à partir des prix du catalogue")
    void createOrder_computesTotalAmount() {
        OrderEntity order = new OrderEntity();
        order.setCustomerId(CUSTOMER_ID);
        order.setItems(new ArrayList<>(List.of(
                item(SKU_1, 2),
                item(SKU_2, 3)
        )));
        stubCatalog(SKU_1, PRODUCT_NAME, PRICE_TEN);
        stubCatalog(SKU_2, "Mouse", new BigDecimal("5.50"));
        givenNoExistingOrderAndSaveEchoesBack();

        OrderEntity saved = orderService.createOrder(order);

        // (2 * 10.00) + (3 * 5.50) = 36.50
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("36.50");
    }

    @Test
    @DisplayName("createOrder positionne le statut PENDING, les dates et lie les articles à la commande")
    void createOrder_initializesOrderState() {
        OrderEntity order = orderWithItem(SKU_1, 1, null);
        stubCatalog(SKU_1, PRODUCT_NAME, PRICE_TEN);
        givenNoExistingOrderAndSaveEchoesBack();

        OrderEntity saved = orderService.createOrder(order);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getItems()).allSatisfy(it -> assertThat(it.getOrder()).isSameAs(saved));
    }

    @Test
    @DisplayName("createOrder persiste la commande et publie un OrderPlacedEvent cohérent")
    void createOrder_savesAndPublishesEvent() {
        OrderEntity order = orderWithItem(SKU_1, 2, null);
        order.setCustomerId("cust-42");
        stubCatalog(SKU_1, PRODUCT_NAME, PRICE_TEN);
        givenNoExistingOrderAndSaveEchoesBack();

        orderService.createOrder(order);

        verify(orderRepository).save(any(OrderEntity.class));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventProducer).sendOrderPlaced(captor.capture());
        OrderPlacedEvent event = captor.getValue();
        assertThat(event.getVersion()).isEqualTo("v1");
        assertThat(event.getCustomerId()).isEqualTo("cust-42");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(event.getItems()).hasSize(1);
        assertThat(event.getItems().get(0).getSku()).isEqualTo(SKU_1);
        assertThat(event.getItems().get(0).getUnitPrice()).isEqualByComparingTo(PRICE_TEN);
    }

    @Test
    @DisplayName("createOrder échoue si un SKU n'existe pas dans le catalogue")
    void createOrder_throws_whenProductNotInCatalog() {
        OrderEntity order = orderWithItem("UNKNOWN", 1, null);
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(catalogClient.getProductBySku("UNKNOWN")).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNKNOWN");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventProducer);
    }

    @Test
    @DisplayName("createOrder échoue quand la commande ne contient aucun article")
    void createOrder_throws_whenNoItems() {
        OrderEntity order = new OrderEntity();
        order.setItems(new ArrayList<>());
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(order))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder échoue quand une quantité est <= 0")
    void createOrder_throws_whenQuantityNotPositive() {
        OrderEntity order = orderWithItem(SKU_1, 0, null);
        stubCatalog(SKU_1, PRODUCT_NAME, PRICE_TEN);
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(order))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orderRepository, never()).save(any());
    }

    // ----------------------------------------------------------------------
    //                       getOrderByOrderId
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("getOrderByOrderId renvoie la commande quand elle existe")
    void getOrderByOrderId_returnsOrder_whenFound() {
        UUID id = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setOrderId(id);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrderByOrderId(id)).isSameAs(order);
    }

    @Test
    @DisplayName("getOrderByOrderId lève OrderNotFoundException quand elle est absente")
    void getOrderByOrderId_throws_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderByOrderId(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ----------------------------------------------------------------------
    //                            helpers
    // ----------------------------------------------------------------------

    private void givenNoExistingOrderAndSaveEchoesBack() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        // Simule la génération de la clé primaire par JPA au moment du save,
        // nécessaire car toOrderPlacedEvent() lit orderId pour la clé Kafka.
        when(orderRepository.save(any())).thenAnswer(inv -> {
            OrderEntity saved = inv.getArgument(0);
            if (saved.getOrderId() == null) {
                saved.setOrderId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private OrderEntity orderWithItem(String sku, int quantity, BigDecimal unitPrice) {
        OrderEntity order = new OrderEntity();
        order.setCustomerId(CUSTOMER_ID);
        order.setItems(new ArrayList<>(List.of(item(sku, quantity, unitPrice))));
        return order;
    }

    private OrderItemEntity item(String sku, int quantity) {
        return item(sku, quantity, null);
    }

    private OrderItemEntity item(String sku, int quantity, BigDecimal unitPrice) {
        OrderItemEntity item = new OrderItemEntity();
        item.setSku(sku);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private void stubCatalog(String sku, String name, BigDecimal price) {
        when(catalogClient.getProductBySku(sku)).thenReturn(
                ProductResponseDTO.builder().sku(sku).name(name).price(price).build());
    }
}