package com.example.hackathon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agents")
@Data
@NoArgsConstructor
public class Agent {

    @Id
    private String id; // AGT-001 format

    private String name;

    private int activeOrderCount = 0;

    @Enumerated(EnumType.STRING)
    private AgentStatus status = AgentStatus.AVAILABLE;

    // Sprint 2 placeholders — no migration needed later
    private String currentZone;
    private Integer maxCapacity;

    public enum AgentStatus {
        AVAILABLE, BUSY, OFFLINE
    }
}
