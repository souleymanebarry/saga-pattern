package com.barry.saga.retail.orderservice.kafka.listener;

import com.barry.saga.retail.order.event.OrderConfirmedEvent;
import com.barry.saga.retail.order.event.OrderFailedEvent;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.outbox.OrderEventPublisher;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.stock.event.StockRejectedEvent;
import com.barry.saga.retail.stock.event.StockReservedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    private static final String CUSTOMER_ID = "cust-1";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("onStockReserved passe la commande à STOCK_RESERVED puis CONFIRMED, publie OrderConfirmedEvent et acquitte")
    void onStockReserved_confirmsOrderAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = existingOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockReserved(reserved(orderId), ack);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        // une sauvegarde par transition : STOCK_RESERVED puis CONFIRMED
        verify(orderRepository, times(2)).save(order);

        ArgumentCaptor<OrderConfirmedEvent> captor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(orderEventPublisher).publishOrderConfirmed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId.toString());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("20.00");

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockReserved ne fait rien mais acquitte quand la commande est introuvable")
    void onStockReserved_onlyAcks_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockReserved(reserved(orderId), ack);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected passe la commande à FAILED, publie OrderFailedEvent et acquitte")
    void onStockRejected_failsOrderAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = existingOrder(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockRejected(rejected(orderId, "out of stock"), ack);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(order);

        ArgumentCaptor<OrderFailedEvent> captor = ArgumentCaptor.forClass(OrderFailedEvent.class);
        verify(orderEventPublisher).publishOrderFailed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId.toString());
        assertThat(captor.getValue().getReason()).isEqualTo("out of stock");

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected ne fait rien mais acquitte quand la commande est introuvable")
    void onStockRejected_onlyAcks_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockRejected(rejected(orderId, "x"), ack);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
        verify(ack).acknowledge();
    }

    private static OrderEntity existingOrder(UUID orderId) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setCustomerId(CUSTOMER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("20"));
        return order;
    }

    private static StockReservedEvent reserved(UUID orderId) {
        StockReservedEvent event = new StockReservedEvent();
        event.setOrderId(orderId.toString());
        return event;
    }

    private static StockRejectedEvent rejected(UUID orderId, String reason) {
        StockRejectedEvent event = new StockRejectedEvent();
        event.setOrderId(orderId.toString());
        event.setReason(reason);
        return event;
    }
}
