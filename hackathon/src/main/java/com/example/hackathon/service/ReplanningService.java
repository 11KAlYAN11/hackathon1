package com.example.hackathon.service;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.event.AgentOfflineEvent;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import com.example.hackathon.repository.ReassignmentSuggestionRepository;
import com.example.hackathon.routing.RoutingContext;
import com.example.hackathon.routing.RoutingResult;
import com.example.hackathon.routing.RoutingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Agentic re-planning loop — T4.
 * Fires asynchronously when an agent goes offline.
 * The PATCH /agents/{id}/status endpoint returns immediately;
 * this listener picks up the work off the request path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanningService {

    private final Map<String, RoutingStrategy> strategies;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;

    @Value("${routing.strategy:ai}")
    private String activeStrategy;

    @Async
    @EventListener
    @Transactional
    public void onAgentOffline(AgentOfflineEvent event) {
        Agent offlineAgent = event.getAgent();
        List<String> orderIds = event.getAffectedOrderIds();

        log.info("Agentic replan triggered: agent {} offline, {} orders stranded",
            offlineAgent.getId(), orderIds.size());

        List<Agent> available = agentRepository.findByStatus(Agent.AgentStatus.AVAILABLE);
        if (available.isEmpty()) {
            log.warn("No available agents for replan — suggestions cannot be created");
            return;
        }

        RoutingContext ctx = RoutingContext.builder()
            .triggerReason(ReassignmentSuggestion.TriggerReason.AGENT_OFFLINE)
            .offlineAgentId(offlineAgent.getId())
            .totalStranded(orderIds.size())
            .build();

        for (String orderId : orderIds) {
            processOrder(orderId, available, ctx);
        }
    }

    private void processOrder(String orderId, List<Agent> available, RoutingContext ctx) {
        // Idempotency — skip if PENDING AGENT_OFFLINE suggestion already exists
        boolean alreadyPending = suggestionRepository
            .findByOrderIdAndStatusAndTriggerReason(
                orderId,
                ReassignmentSuggestion.SuggestionStatus.PENDING,
                ReassignmentSuggestion.TriggerReason.AGENT_OFFLINE)
            .isPresent();

        if (alreadyPending) {
            log.debug("Skipping order {} — PENDING AGENT_OFFLINE suggestion already exists", orderId);
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        RoutingResult result = route(order, available, ctx);

        ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
        suggestion.setOrder(order);
        suggestion.setRecommendedAgent(result.getRecommendedAgent());
        suggestion.setConfidence(result.getConfidence());
        suggestion.setReasoning(result.getReasoning());
        suggestion.setTriggerReason(ReassignmentSuggestion.TriggerReason.AGENT_OFFLINE);
        suggestionRepository.save(suggestion);

        order.setStatus(Order.OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        log.info("Replan suggestion created for order {} → agent {}",
            orderId, result.getRecommendedAgent().getId());
    }

    private RoutingResult route(Order order, List<Agent> available, RoutingContext ctx) {
        RoutingStrategy strategy = strategies.getOrDefault(activeStrategy, strategies.get("rule-based"));
        try {
            return strategy.recommend(order, available, ctx);
        } catch (Exception e) {
            log.warn("Active strategy '{}' failed during replan, using rule-based fallback", activeStrategy);
            return strategies.get("rule-based").recommend(order, available, ctx);
        }
    }
}
