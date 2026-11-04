package com.barry.saga.retail.orderservice.controller.impl;

import com.barry.saga.retail.orderservice.dtos.OrderResponse;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.exceptions.GlobalExceptionHandler;
import com.barry.saga.retail.orderservice.exceptions.InvalidOrderException;
import com.barry.saga.retail.orderservice.exceptions.OrderNotFoundException;
import com.barry.saga.retail.orderservice.exceptions.ProductNotFoundException;
import com.barry.saga.retail.orderservice.mappers.OrderWebMapper;
import com.barry.saga.retail.orderservice.service.OrderService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerImplTest {

    private static final String ORDER_JSON = """
            {"customerId": "CUST-001", "items": [{"sku": "SKU-1", "quantity": 2}]}
            """;

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
    @SneakyThrows
    @DisplayName("POST /api/v1/orders transmet la clé du header Idempotency-Key au service et la renvoie")
    void createOrder_usesClientIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID orderId = UUID.randomUUID();
        OrderEntity mapped = new OrderEntity();
        when(orderWebMapper.toOrderEntity(any())).thenReturn(mapped);
        when(orderService.createOrder(mapped)).thenReturn(mapped);
        when(orderWebMapper.toResponse(mapped))
                .thenReturn(OrderResponse.builder().orderId(orderId).status("PENDING").build());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Key", idempotencyKey))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));

        ArgumentCaptor<OrderEntity> captor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderService).createOrder(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @SneakyThrows
    @DisplayName("POST /api/v1/orders renvoie 400 sans header Idempotency-Key")
    void createOrder_returns400WhenIdempotencyKeyMissing() {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing request header"))
                .andExpect(jsonPath("$.detail").value("Idempotency-Key header is required"));

        verifyNoInteractions(orderService);
    }

    @Test
    @SneakyThrows
    @DisplayName("POST /api/v1/orders renvoie 400 si le header Idempotency-Key est vide")
    void createOrder_returns400WhenIdempotencyKeyBlank() {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing idempotency key"));

        verifyNoInteractions(orderService);
    }

    @Test
    @SneakyThrows
    @DisplayName("POST /api/v1/orders renvoie 400 si la commande est invalide")
    void createOrder_returns400WhenOrderInvalid() {
        when(orderWebMapper.toOrderEntity(any())).thenReturn(new OrderEntity());
        when(orderService.createOrder(any()))
                .thenThrow(new InvalidOrderException("An order must contain at least one item"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid order"))
                .andExpect(jsonPath("$.detail").value("An order must contain at least one item"));
    }

    @Test
    @SneakyThrows
    @DisplayName("POST /api/v1/orders renvoie 422 si un SKU n'existe pas dans le catalogue")
    void createOrder_returns422WhenProductNotFound() {
        when(orderWebMapper.toOrderEntity(any())).thenReturn(new OrderEntity());
        when(orderService.createOrder(any())).thenThrow(new ProductNotFoundException("SKU-1"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.detail").value("Product not found in catalog: SKU-1"));
    }

    @Test
    @SneakyThrows
    @DisplayName("GET /api/v1/orders/{id} renvoie 200 avec la commande")
    void getOrderById_returnsOrder() {
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
    @SneakyThrows
    @DisplayName("GET /api/v1/orders/{id} renvoie 404 si la commande n'existe pas")
    void getOrderById_returns404WhenNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrderByOrderId(orderId))
                .thenThrow(new OrderNotFoundException(orderId.toString()));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: " + orderId));
    }
}
