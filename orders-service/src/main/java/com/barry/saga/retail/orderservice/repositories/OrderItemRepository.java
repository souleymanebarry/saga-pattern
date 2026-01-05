package com.barry.saga.retail.orderservice.repositories;

import com.barry.saga.retail.orderservice.entities.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, UUID> {

}
