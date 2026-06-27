package com.example.hackathon.routing;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Rule-based strategy: prefer AVAILABLE agents over BUSY, then sort by active order count.
 * Includes BUSY agents in the pool so load is spread across the whole fleet, not just
 * agents who happen to be marked AVAILABLE at routing time.
 */
@Component("rule-based")
public class RuleBasedStrategy implements RoutingStrategy {

    @Override
    public RoutingResult recommend(Order order, List<Agent> candidates, RoutingContext context) {
        // Pool = everyone not OFFLINE; prefer AVAILABLE first, then BUSY — both sorted by load
        List<Agent> pool = candidates.stream()
            .filter(a -> a.getStatus() != Agent.AgentStatus.OFFLINE)
            .sorted(Comparator
                .comparingInt((Agent a) -> a.getStatus() == Agent.AgentStatus.AVAILABLE ? 0 : 1)
                .thenComparingInt(Agent::getActiveOrderCount))
            .toList();

        if (pool.isEmpty()) {
            throw new IllegalStateException("No non-offline agents available for routing");
        }

        Agent best = pool.get(0);
        int maxLoad = pool.stream().mapToInt(Agent::getActiveOrderCount).max().orElse(0);
        double loadAdvantage = maxLoad == 0 ? 1.0 : (double)(maxLoad - best.getActiveOrderCount()) / maxLoad;
        double confidence = Math.round((0.62 + 0.26 * loadAdvantage) * 100.0) / 100.0;

        String statusNote = best.getStatus() == Agent.AgentStatus.AVAILABLE
            ? "currently free"
            : "lightest among BUSY agents";

        String reasoning = ("%s (%s, %d active orders) is the best fit — "
            + "pool avg is %.1f orders across %d agent%s. "
            + "Assigning here minimises delay risk without overloading any single agent.")
            .formatted(
                best.getName(), statusNote, best.getActiveOrderCount(),
                pool.stream().mapToInt(Agent::getActiveOrderCount).average().orElse(0),
                pool.size(), pool.size() == 1 ? "" : "s"
            );

        return RoutingResult.builder()
            .recommendedAgent(best)
            .confidence(confidence)
            .reasoning(reasoning)
            .build();
    }
}
