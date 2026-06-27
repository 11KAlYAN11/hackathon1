package com.example.hackathon.routing;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;

import java.util.List;

/**
 * Contract for all routing strategies.
 * Both the HTTP endpoint and the async agentic loop call this interface —
 * neither caller needs to know which implementation is active.
 *
 * Sprint 2: add ZoneAffinityStrategy by implementing this interface.
 * No existing code changes needed.
 */
public interface RoutingStrategy {
    RoutingResult recommend(Order order, List<Agent> availableAgents, RoutingContext context);
}
