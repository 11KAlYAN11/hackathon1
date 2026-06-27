package com.example.hackathon.routing;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Rule-based strategy: pick the available agent carrying the fewest active orders.
 * Deterministic, no external dependencies — solid fallback when AI is unavailable.
 */
@Component("rule-based")
public class RuleBasedStrategy implements RoutingStrategy {

    @Override
    public RoutingResult recommend(Order order, List<Agent> availableAgents, RoutingContext context) {
        Agent best = availableAgents.stream()
            .filter(a -> a.getStatus() == Agent.AgentStatus.AVAILABLE)
            .min(Comparator.comparingInt(Agent::getActiveOrderCount))
            .orElseThrow(() -> new IllegalStateException("No available agents for routing"));

        return RoutingResult.builder()
            .recommendedAgent(best)
            .confidence(0.6)
            .reasoning("Rule-based: selected agent with fewest active orders (%d)".formatted(best.getActiveOrderCount()))
            .build();
    }
}
