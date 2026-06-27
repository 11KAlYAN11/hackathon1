package com.example.hackathon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    private String id; // ORD-001 format

    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.ASSIGNED;

    private Instant createdAt = Instant.now();

    // Sprint 2 placeholders
    private String pickupZone;
    private String dropoffZone;
    private String weightClass; // LIGHT | HEAVY
    private Instant slaDeadline; // Sprint 3: proactive SLA loop

    public enum OrderStatus {
        ASSIGNED, REASSIGNMENT_PENDING, REASSIGNED, DELIVERED
    }
}
