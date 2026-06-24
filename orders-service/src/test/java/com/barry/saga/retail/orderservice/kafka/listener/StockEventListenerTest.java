package com.barry.saga.retail.orderservice.kafka.listener;

import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import com.barry.saga.retail.orderservice.repositories.OrderRepository;
import com.barry.saga.retail.orderservice.share.event.StockRejectedEvent;
import com.barry.saga.retail.orderservice.share.event.StockReservedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockEventListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private StockEventListener listener;

    @Test
    @DisplayName("onStockReserved passe la commande à STOCK_CHECKING, persiste et acquitte")
    void onStockReserved_updatesStatusAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockReserved(StockReservedEvent.builder().orderId(orderId).build(), ack);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_CHECKING);
        verify(orderRepository).save(order);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockReserved n'acquitte pas quand la commande est introuvable")
    void onStockReserved_doesNothing_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockReserved(StockReservedEvent.builder().orderId(orderId).build(), ack);

        verify(orderRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected passe la commande à FAILED, persiste et acquitte")
    void onStockRejected_updatesStatusAndAcks() {
        UUID orderId = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        listener.onStockRejected(
                StockRejectedEvent.builder().orderId(orderId).reason("out of stock").build(), ack);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository).save(order);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("onStockRejected n'acquitte pas quand la commande est introuvable")
    void onStockRejected_doesNothing_whenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onStockRejected(
                StockRejectedEvent.builder().orderId(orderId).reason("x").build(), ack);

        verify(orderRepository, never()).save(any());
        verify(ack, never()).acknowledge();
    }
}