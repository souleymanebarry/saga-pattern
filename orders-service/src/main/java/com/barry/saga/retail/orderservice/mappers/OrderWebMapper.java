package com.barry.saga.retail.orderservice.mappers;

import com.barry.saga.retail.orderservice.dtos.CreateOrderRequest;
import com.barry.saga.retail.orderservice.dtos.OrderItemRequest;
import com.barry.saga.retail.orderservice.dtos.OrderItemResponse;
import com.barry.saga.retail.orderservice.dtos.OrderResponse;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;

/**
 * Mapper Web responsable de la conversion entre :
 * - DTOs reçus depuis l’API
 * - Entités JPA utilisées dans le domaine
 *
 * Ce mapper ne doit pas être utilisé dans le service métier.
 */

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {LocalDateTime.class, OrderStatus.class}
)
public interface OrderWebMapper {

    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrderEntity toOrderEntity(CreateOrderRequest request);

    @Mapping(target = "order", ignore = true)
    OrderItemEntity toItemEntity(OrderItemRequest dto);

    @Mapping(target = "items", source = "items")
    @Mapping(target = "status", source = "status")
    OrderResponse toResponse(OrderEntity entity);

    @Mapping(target = "subtotal",
            expression = "java(entity.getUnitPrice().multiply(java.math.BigDecimal.valueOf(entity.getQuantity())))")
    OrderItemResponse toItemResponse(OrderItemEntity entity);
}
