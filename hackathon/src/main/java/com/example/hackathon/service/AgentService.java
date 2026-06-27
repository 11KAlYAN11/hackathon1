package com.example.hackathon.service;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.event.AgentOfflineEvent;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AgentService {

    private final AgentRepository agentRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<Agent> getAll() {
        return agentRepository.findAll();
    }

    /**
     * Updates agent status. If transitioning to OFFLINE, publishes an
     * AgentOfflineEvent — the agentic loop picks it up asynchronously.
     * This method returns immediately regardless of re-planning time.
     */
    public Agent updateStatus(String agentId, Agent.AgentStatus newStatus) {
        Agent agent = agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        Agent.AgentStatus previous = agent.getStatus();
        agent.setStatus(newStatus);
        Agent saved = agentRepository.save(agent);

        if (previous != Agent.AgentStatus.OFFLINE && newStatus == Agent.AgentStatus.OFFLINE) {
            // Include ASSIGNED, REASSIGNED, and REASSIGNMENT_PENDING —
            // an agent can go offline again after already receiving reassigned orders
            List<String> affected = orderRepository.findByAssignedAgentId(agentId)
                .stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.ASSIGNED
                          || o.getStatus() == Order.OrderStatus.REASSIGNED
                          || o.getStatus() == Order.OrderStatus.REASSIGNMENT_PENDING)
                .map(Order::getId)
                .toList();

            if (!affected.isEmpty()) {
                log.info("Agent {} went OFFLINE with {} assigned orders — publishing replan event",
                    agentId, affected.size());
                eventPublisher.publishEvent(new AgentOfflineEvent(this, saved, affected));
            }
        }

        return saved;
    }
}
