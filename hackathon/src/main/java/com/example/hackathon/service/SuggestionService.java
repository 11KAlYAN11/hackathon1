package com.example.hackathon.service;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import com.example.hackathon.repository.ReassignmentSuggestionRepository;
import com.example.hackathon.routing.RoutingContext;
import com.example.hackathon.routing.RoutingResult;
import com.example.hackathon.routing.RoutingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SuggestionService {

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final Map<String, RoutingStrategy> strategies;

    @Value("${routing.strategy:ai}")
    private String activeStrategy;

    public List<ReassignmentSuggestion> getAll() {
        return suggestionRepository.findAll();
    }

    /** PATCH /suggestions/{id} — ops accepts or rejects */
    public ReassignmentSuggestion updateStatus(Long id, ReassignmentSuggestion.SuggestionStatus newStatus) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));

        suggestion.setStatus(newStatus);
        suggestionRepository.save(suggestion);

        if (newStatus == ReassignmentSuggestion.SuggestionStatus.ACCEPTED) {
            Order order = suggestion.getOrder();
            Agent agent = suggestion.getRecommendedAgent();

            // Decrement old agent count
            if (order.getAssignedAgent() != null) {
                Agent old = order.getAssignedAgent();
                old.setActiveOrderCount(Math.max(0, old.getActiveOrderCount() - 1));
                agentRepository.save(old);
            }

            // Capacity was already reserved at suggestion-creation time — do NOT increment again.
            if (agent.getActiveOrderCount() > 0) agent.setStatus(Agent.AgentStatus.BUSY);
            agentRepository.save(agent);

            order.setAssignedAgent(agent);
            order.setStatus(Order.OrderStatus.REASSIGNED);
            orderRepository.save(order);

        } else if (newStatus == ReassignmentSuggestion.SuggestionStatus.REJECTED) {
            // Release the optimistic reservation
            Agent rejected = suggestion.getRecommendedAgent();
            rejected.setActiveOrderCount(Math.max(0, rejected.getActiveOrderCount() - 1));
            agentRepository.save(rejected);

            // Auto-generate a new suggestion so the order never gets stuck
            autoRetry(suggestion);
        }

        return suggestion;
    }

    /**
     * Called after a REJECT. Re-runs routing (excluding the just-rejected agent)
     * and creates a fresh PENDING suggestion for the same order.
     */
    private void autoRetry(ReassignmentSuggestion rejected) {
        Order order = rejected.getOrder();
        String rejectedAgentId = rejected.getRecommendedAgent().getId();

        List<Agent> candidates = agentRepository.findAll().stream()
            .filter(a -> a.getStatus() != Agent.AgentStatus.OFFLINE
                      && !a.getId().equals(rejectedAgentId))
            .toList();

        if (candidates.isEmpty()) {
            log.warn("Auto-retry for order {} — no alternative agents available", order.getId());
            return;
        }

        RoutingContext ctx = RoutingContext.builder()
            .triggerReason(rejected.getTriggerReason())
            .offlineAgentId(rejectedAgentId)
            .totalStranded(1)
            .build();

        RoutingResult result;
        try {
            RoutingStrategy strategy = strategies.getOrDefault(activeStrategy, strategies.get("rule-based"));
            result = strategy.recommend(order, candidates, ctx);
        } catch (Exception e) {
            log.warn("Auto-retry routing failed for order {}, using rule-based fallback", order.getId());
            result = strategies.get("rule-based").recommend(order, candidates, ctx);
        }

        ReassignmentSuggestion retry = new ReassignmentSuggestion();
        retry.setOrder(order);
        retry.setRecommendedAgent(result.getRecommendedAgent());
        retry.setConfidence(result.getConfidence());
        retry.setReasoning("[Retry after rejection] " + result.getReasoning());
        retry.setTriggerReason(rejected.getTriggerReason());
        suggestionRepository.save(retry);

        // Reserve capacity on the new recommendation, mark BUSY immediately
        Agent next = result.getRecommendedAgent();
        next.setActiveOrderCount(next.getActiveOrderCount() + 1);
        next.setStatus(Agent.AgentStatus.BUSY);
        agentRepository.save(next);

        log.info("Auto-retry suggestion created for order {} → agent {}", order.getId(), next.getId());
    }
}
