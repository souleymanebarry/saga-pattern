package com.barry.saga.retail.orderservice.kafka.listener;

import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.kafka.OrderEventProducer;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.stock.event.StockRejectedEvent;
import com.barry.saga.retail.stock.event.StockReservedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer orderEventProducer;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("onStockReserved confirme la commande (CONFIRMED), persiste, publie et acquitte")
    void onStockReserved_confirmsAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = orderWith(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockReserved(stockReserved(orderId), ack);

        // STOCK_RESERVED puis CONFIRMED → deux save(), un OrderConfirmedEvent publié
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, times(2)).save(order);
        verify(orderEventProducer).sendOrderConfirmed(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockReserved acquitte quand même quand la commande est introuvable (anti poison pill)")
    void onStockReserved_acksAndSkips_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockReserved(stockReserved(orderId), ack);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderConfirmed(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected passe la commande à FAILED, persiste, publie et acquitte")
    void onStockRejected_failsAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = orderWith(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockRejected(stockRejected(orderId, "out of stock"), ack);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(order);
        verify(orderEventProducer).sendOrderFailed(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected acquitte quand même quand la commande est introuvable")
    void onStockRejected_acksAndSkips_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockRejected(stockRejected(orderId, "x"), ack);

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderFailed(any());
        verify(ack).acknowledge();
    }

    // ----------------------------------------------------------------------
    //                            helpers
    // ----------------------------------------------------------------------

    private OrderEntity orderWith(UUID orderId) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setCustomerId("cust-1");
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("10.00"));
        return order;
    }

    private StockReservedEvent stockReserved(UUID orderId) {
        StockReservedEvent event = mock(StockReservedEvent.class);
        when(event.getOrderId()).thenReturn(orderId.toString());
        return event;
    }

    private StockRejectedEvent stockRejected(UUID orderId, String reason) {
        StockRejectedEvent event = mock(StockRejectedEvent.class);
        when(event.getOrderId()).thenReturn(orderId.toString());
        when(event.getReason()).thenReturn(reason);
        return event;
    }
}