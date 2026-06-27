package com.example.hackathon.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "reassignment_suggestions")
@Data
@NoArgsConstructor
public class ReassignmentSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recommended_agent_id")
    private Agent recommendedAgent;

    private Double confidence; // 0.0 – 1.0

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private TriggerReason triggerReason; // INITIAL | AGENT_OFFLINE

    private Instant createdAt = Instant.now();

    public enum SuggestionStatus {
        PENDING, ACCEPTED, REJECTED
    }

    public enum TriggerReason {
        INITIAL, AGENT_OFFLINE
    }
}
