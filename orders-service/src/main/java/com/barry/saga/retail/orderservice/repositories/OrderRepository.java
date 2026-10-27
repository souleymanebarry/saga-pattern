package com.barry.saga.retail.orderservice.repositories;

import com.barry.saga.retail.orderservice.entities.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

}
