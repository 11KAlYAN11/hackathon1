package com.example.hackathon.repository;

import com.example.hackathon.entity.ReassignmentSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReassignmentSuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {
    List<ReassignmentSuggestion> findByOrderId(String orderId);

    // Idempotency check — skip if PENDING AGENT_OFFLINE suggestion already exists
    Optional<ReassignmentSuggestion> findByOrderIdAndStatusAndTriggerReason(
        String orderId,
        ReassignmentSuggestion.SuggestionStatus status,
        ReassignmentSuggestion.TriggerReason triggerReason
    );
}
