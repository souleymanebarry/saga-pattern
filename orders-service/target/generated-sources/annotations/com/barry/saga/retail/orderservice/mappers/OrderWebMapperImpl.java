package com.barry.saga.retail.orderservice.mappers;

import com.barry.saga.retail.orderservice.dtos.CreateOrderRequest;
import com.barry.saga.retail.orderservice.dtos.OrderItemRequest;
import com.barry.saga.retail.orderservice.dtos.OrderItemResponse;
import com.barry.saga.retail.orderservice.dtos.OrderResponse;
import com.barry.saga.retail.orderservice.entities.OrderEntity;
import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import com.barry.saga.retail.orderservice.entities.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-12-23T17:26:37+0100",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.4.1 (Oracle Corporation)"
)
@Component
public class OrderWebMapperImpl implements OrderWebMapper {

    @Override
    public OrderEntity toOrderEntity(CreateOrderRequest request) {
        if ( request == null ) {
            return null;
        }

        OrderEntity.OrderEntityBuilder orderEntity = OrderEntity.builder();

        orderEntity.customerId( request.getCustomerId() );
        orderEntity.items( orderItemRequestListToOrderItemEntityList( request.getItems() ) );
        orderEntity.idempotencyKey( request.getIdempotencyKey() );

        return orderEntity.build();
    }

    @Override
    public OrderItemEntity toItemEntity(OrderItemRequest dto) {
        if ( dto == null ) {
            return null;
        }

        OrderItemEntity.OrderItemEntityBuilder orderItemEntity = OrderItemEntity.builder();

        orderItemEntity.sku( dto.getSku() );
        orderItemEntity.quantity( dto.getQuantity() );

        return orderItemEntity.build();
    }

    @Override
    public OrderResponse toResponse(OrderEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrderResponse.OrderResponseBuilder orderResponse = OrderResponse.builder();

        orderResponse.items( orderItemEntityListToOrderItemResponseList( entity.getItems() ) );
        if ( entity.getStatus() != null ) {
            orderResponse.status( entity.getStatus().name() );
        }
        orderResponse.orderId( entity.getOrderId() );
        orderResponse.customerId( entity.getCustomerId() );
        orderResponse.totalAmount( entity.getTotalAmount() );
        orderResponse.createdAt( entity.getCreatedAt() );
        orderResponse.updatedAt( entity.getUpdatedAt() );
        orderResponse.idempotencyKey( entity.getIdempotencyKey() );

        return orderResponse.build();
    }

    @Override
    public OrderItemResponse toItemResponse(OrderItemEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OrderItemResponse.OrderItemResponseBuilder orderItemResponse = OrderItemResponse.builder();

        orderItemResponse.orderItemId( entity.getOrderItemId() );
        orderItemResponse.sku( entity.getSku() );
        orderItemResponse.productName( entity.getProductName() );
        orderItemResponse.quantity( entity.getQuantity() );
        orderItemResponse.unitPrice( entity.getUnitPrice() );

        orderItemResponse.subtotal( entity.getUnitPrice().multiply(java.math.BigDecimal.valueOf(entity.getQuantity())) );

        return orderItemResponse.build();
    }

    protected List<OrderItemEntity> orderItemRequestListToOrderItemEntityList(List<OrderItemRequest> list) {
        if ( list == null ) {
            return null;
        }

        List<OrderItemEntity> list1 = new ArrayList<OrderItemEntity>( list.size() );
        for ( OrderItemRequest orderItemRequest : list ) {
            list1.add( toItemEntity( orderItemRequest ) );
        }

        return list1;
    }

    protected List<OrderItemResponse> orderItemEntityListToOrderItemResponseList(List<OrderItemEntity> list) {
        if ( list == null ) {
            return null;
        }

        List<OrderItemResponse> list1 = new ArrayList<OrderItemResponse>( list.size() );
        for ( OrderItemEntity orderItemEntity : list ) {
            list1.add( toItemResponse( orderItemEntity ) );
        }

        return list1;
    }
}
