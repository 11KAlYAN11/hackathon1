package com.example.hackathon.repository;

import com.example.hackathon.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByStatus(Order.OrderStatus status);
    List<Order> findByAssignedAgentId(String agentId);
}
