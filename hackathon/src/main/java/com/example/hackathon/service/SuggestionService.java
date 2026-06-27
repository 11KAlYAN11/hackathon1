package com.example.hackathon.service;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import com.example.hackathon.repository.ReassignmentSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SuggestionService {

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public List<ReassignmentSuggestion> getAll() {
        return suggestionRepository.findAll();
    }

    /** PATCH /suggestions/{id} — ops accepts or rejects */
    public ReassignmentSuggestion updateStatus(Long id, ReassignmentSuggestion.SuggestionStatus newStatus) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + id));

        suggestion.setStatus(newStatus);

        if (newStatus == ReassignmentSuggestion.SuggestionStatus.ACCEPTED) {
            Order order = suggestion.getOrder();
            Agent agent = suggestion.getRecommendedAgent();

            // Decrement old agent count
            if (order.getAssignedAgent() != null) {
                Agent old = order.getAssignedAgent();
                old.setActiveOrderCount(Math.max(0, old.getActiveOrderCount() - 1));
                agentRepository.save(old);
            }

            // Assign to new agent
            agent.setActiveOrderCount(agent.getActiveOrderCount() + 1);
            if (agent.getActiveOrderCount() > 0) agent.setStatus(Agent.AgentStatus.BUSY);
            agentRepository.save(agent);

            order.setAssignedAgent(agent);
            order.setStatus(Order.OrderStatus.REASSIGNED);
            orderRepository.save(order);
        }

        return suggestionRepository.save(suggestion);
    }
}
