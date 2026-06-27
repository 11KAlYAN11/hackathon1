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

    public Order create(Order order) {
        return orderRepository.save(order);
    }

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
