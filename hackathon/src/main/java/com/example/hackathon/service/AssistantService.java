package com.example.hackathon.service;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.llm.LLMGateway;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssistantService {

    private final AgentRepository agentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final LLMGateway llmGateway;
    private final ObjectMapper objectMapper;

    public record AssistantResponse(String message, String actionType, Object data) {}

    public AssistantResponse chat(String userMessage) {
        List<Agent> agents = agentRepository.findAll();
        List<Order> orders = orderRepository.findAll();

        String fleetSnapshot = buildFleetSnapshot(agents, orders);
        String prompt = buildPrompt(userMessage, fleetSnapshot, agents);

        String raw;
        try {
            raw = llmGateway.callLLM(prompt);
        } catch (Exception e) {
            log.warn("Assistant LLM call failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            String hint = e.getMessage() != null && e.getMessage().contains("401")
                ? " (API key invalid or missing — set LLM_API_KEY in run config)"
                : e.getMessage() != null && e.getMessage().contains("404")
                ? " (wrong API URL — check LLM_BASE_URL config)"
                : "";
            return new AssistantResponse(
                "AI unavailable" + hint + ". Fleet at a glance: " + quickSummary(agents, orders),
                "TEXT", null
            );
        }

        try {
            String cleaned = stripMarkdown(raw);
            JsonNode json = objectMapper.readTree(cleaned);
            String action = json.path("action").asText("TEXT");
            String message = json.path("message").asText(raw);

            if ("CREATE_ORDER".equals(action)) {
                String desc = json.path("orderDescription").asText("");
                String agentId = json.path("agentId").asText("");
                if (!desc.isBlank()) {
                    try {
                        Order created = orderService.create(
                            new OrderService.OrderCreateRequest(desc, agentId.isBlank() ? null : agentId)
                        );
                        return new AssistantResponse(message, "ORDER_CREATED", created);
                    } catch (Exception ex) {
                        return new AssistantResponse("Couldn't create order: " + ex.getMessage(), "ERROR", null);
                    }
                }
            }

            return new AssistantResponse(message, action, null);

        } catch (Exception e) {
            // LLM returned plain text instead of JSON — still useful
            return new AssistantResponse(raw.strip(), "TEXT", null);
        }
    }

    private String buildPrompt(String userMessage, String fleetSnapshot, List<Agent> agents) {
        String agentList = agents.stream()
            .map(a -> "  %s — %s · %d orders".formatted(a.getName(), a.getStatus(), a.getActiveOrderCount()))
            .collect(Collectors.joining("\n"));

        return """
            You are ZipRun Ops AI — an assistant for a delivery fleet operations console.
            You can answer questions about the fleet, summarise orders, recommend agents, and create new orders.

            CURRENT FLEET STATE:
            %s

            AGENTS:
            %s

            USER MESSAGE: %s

            If the user wants to create an order, respond with JSON:
            {"action":"CREATE_ORDER","orderDescription":"<description>","agentId":"<AGT-xxx or empty for auto>","message":"<confirmation to show user>"}

            For all other responses (summaries, recommendations, Q&A), respond with JSON:
            {"action":"TEXT","message":"<your response in 2-3 concise sentences>"}

            Rules:
            - Be direct and ops-focused. No fluff.
            - For summaries, include numbers. For recommendations, name the agent and why.
            - Never invent data — use only the fleet state provided.
            - Always respond with valid JSON.
            """.formatted(fleetSnapshot, agentList, userMessage);
    }

    private String buildFleetSnapshot(List<Agent> agents, List<Order> orders) {
        long available = agents.stream().filter(a -> a.getStatus() == Agent.AgentStatus.AVAILABLE).count();
        long busy      = agents.stream().filter(a -> a.getStatus() == Agent.AgentStatus.BUSY).count();
        long offline   = agents.stream().filter(a -> a.getStatus() == Agent.AgentStatus.OFFLINE).count();

        long assigned  = orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.ASSIGNED).count();
        long pending   = orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.REASSIGNMENT_PENDING).count();
        long reassigned= orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.REASSIGNED).count();
        long delivered = orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED).count();

        return "Agents: %d available, %d busy, %d offline | Orders: %d assigned, %d reassignment-pending, %d reassigned, %d delivered"
            .formatted(available, busy, offline, assigned, pending, reassigned, delivered);
    }

    private String quickSummary(List<Agent> agents, List<Order> orders) {
        long available = agents.stream().filter(a -> a.getStatus() == Agent.AgentStatus.AVAILABLE).count();
        long pending   = orders.stream().filter(o -> o.getStatus() == Order.OrderStatus.REASSIGNMENT_PENDING).count();
        return "%d agents available, %d orders pending reassignment.".formatted(available, pending);
    }

    private String stripMarkdown(String raw) {
        String t = raw.strip();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n') + 1;
            int end   = t.lastIndexOf("```");
            if (start > 0 && end > start) return t.substring(start, end).strip();
        }
        return t;
    }
}
