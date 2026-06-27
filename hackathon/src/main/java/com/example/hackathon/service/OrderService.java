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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final Map<String, RoutingStrategy> strategies;

    @Value("${routing.strategy:ai}")
    private String activeStrategy;

    public List<Order> getAll(Order.OrderStatus status) {
        return status != null
            ? orderRepository.findByStatus(status)
            : orderRepository.findAll();
    }

    /**
     * POST /orders — create a new order and assign to an available agent.
     * Body: { "description": "...", "assignedAgentId": "AGT-001" }
     * If assignedAgentId is omitted, auto-picks the least-loaded available agent.
     */
    public Order create(OrderCreateRequest req) {
        // Auto-generate next ORD-NNN id
        long count = orderRepository.count();
        String newId = "ORD-%03d".formatted(count + 1);

        Agent agent;
        if (req.assignedAgentId() != null && !req.assignedAgentId().isBlank()) {
            agent = agentRepository.findById(req.assignedAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + req.assignedAgentId()));
        } else {
            agent = agentRepository.findAll().stream()
                .filter(a -> a.getStatus() == Agent.AgentStatus.AVAILABLE
                          || a.getStatus() == Agent.AgentStatus.BUSY)
                .min(Comparator.comparingInt(Agent::getActiveOrderCount))
                .orElseThrow(() -> new IllegalStateException("No agents available to assign"));
        }

        agent.setActiveOrderCount(agent.getActiveOrderCount() + 1);
        if (agent.getStatus() == Agent.AgentStatus.AVAILABLE) agent.setStatus(Agent.AgentStatus.BUSY);
        agentRepository.save(agent);

        Order order = new Order();
        order.setId(newId);
        order.setDescription(req.description());
        order.setAssignedAgent(agent);
        order.setStatus(Order.OrderStatus.ASSIGNED);
        return orderRepository.save(order);
    }

    public record OrderCreateRequest(String description, String assignedAgentId) {}

    /** POST /orders/{id}/suggest — on-demand routing via active strategy */
    public ReassignmentSuggestion suggest(String orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        List<Agent> available = agentRepository.findByStatus(Agent.AgentStatus.AVAILABLE);
        if (available.isEmpty()) throw new IllegalStateException("No available agents");

        RoutingContext ctx = RoutingContext.builder()
            .triggerReason(ReassignmentSuggestion.TriggerReason.INITIAL)
            .build();

        RoutingStrategy strategy = strategies.getOrDefault(activeStrategy, strategies.get("rule-based"));
        RoutingResult result = strategy.recommend(order, available, ctx);

        ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
        suggestion.setOrder(order);
        suggestion.setRecommendedAgent(result.getRecommendedAgent());
        suggestion.setConfidence(result.getConfidence());
        suggestion.setReasoning(result.getReasoning());
        suggestion.setTriggerReason(ReassignmentSuggestion.TriggerReason.INITIAL);

        order.setStatus(Order.OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        return suggestionRepository.save(suggestion);
    }
}
