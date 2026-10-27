package com.barry.saga.retail.orderservice.controller.impl;

import com.barry.saga.retail.orderservice.dtos.OrderResponse;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.exceptions.GlobalExceptionHandler;
import com.barry.saga.retail.orderservice.exceptions.OrderNotFoundException;
import com.barry.saga.retail.orderservice.mappers.OrderWebMapper;
import com.barry.saga.retail.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerImplTest {

    private OrderService orderService;
    private OrderWebMapper orderWebMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        orderWebMapper = mock(OrderWebMapper.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderControllerImpl(orderService, orderWebMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} renvoie 200 avec la commande")
    void getOrderById_returnsOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderEntity entity = OrderEntity.builder().orderId(orderId).build();
        when(orderService.getOrderByOrderId(orderId)).thenReturn(entity);
        when(orderWebMapper.toResponse(entity))
                .thenReturn(OrderResponse.builder().orderId(orderId).status("FAILED").build());

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} renvoie 404 si la commande n'existe pas")
    void getOrderById_returns404WhenNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderByOrderId(orderId))
                .thenThrow(new OrderNotFoundException(orderId.toString()));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: " + orderId));
    }
}
