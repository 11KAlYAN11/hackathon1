package com.example.hackathon.routing;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.llm.LLMGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI routing strategy. Calls the LLM with context-aware prompts —
 * initial suggestion and agent-offline recovery use different prompts
 * because they are genuinely different situations.
 *
 * Falls back to rule-based if LLM fails for any reason.
 */
@Slf4j
@Component("ai")
@RequiredArgsConstructor
public class AiRoutingStrategy implements RoutingStrategy {

    private final LLMGateway llmGateway;
    private final RuleBasedStrategy fallback;
    private final ObjectMapper objectMapper;

    @Override
    public RoutingResult recommend(Order order, List<Agent> availableAgents, RoutingContext context) {
        try {
            String prompt = buildPrompt(order, availableAgents, context);
            String raw    = llmGateway.callLLM(prompt);
            return parseAndValidate(raw, availableAgents, order, context);
        } catch (Exception e) {
            log.warn("AI routing failed for order {}, falling back to rule-based: {}", order.getId(), e.getMessage());
            return fallback.recommend(order, availableAgents, context);
        }
    }

    private String buildPrompt(Order order, List<Agent> agents, RoutingContext ctx) {
        String agentRoster = agents.stream()
            .filter(a -> a.getStatus() != Agent.AgentStatus.OFFLINE)
            .map(a -> "  - agentId: %s | name: %s | status: %s | activeOrders: %d".formatted(
                a.getId(), a.getName(), a.getStatus(), a.getActiveOrderCount()))
            .collect(Collectors.joining("\n"));

        if (ctx.getTriggerReason() == ReassignmentSuggestion.TriggerReason.AGENT_OFFLINE) {
            return """
                You are a delivery ops AI handling an URGENT recovery. Agent %s went OFFLINE — %d order(s) are stranded.
                Do NOT recommend agent %s under any circumstances.

                STRANDED ORDER:
                  ID: %s | %s

                AVAILABLE AGENTS:
                %s

                Pick the best agent. Confidence must reflect actual fit (vary it — don't always pick 0.8).
                Reasoning must be 1-2 sharp sentences an ops manager can act on instantly. No filler words.

                Respond ONLY with valid JSON:
                {"agentId":"<id>","confidence":<0.55-0.95>,"reasoning":"<1-2 crisp sentences>"}
                """.formatted(
                    ctx.getOfflineAgentId(), ctx.getTotalStranded(), ctx.getOfflineAgentId(),
                    order.getId(), order.getDescription(), agentRoster);
        }

        return """
            You are a delivery ops AI. Assign the best available agent for this order.

            ORDER: %s | %s

            AVAILABLE AGENTS:
            %s

            Rules:
            - Confidence must genuinely reflect fit (0.55–0.95). Vary it based on workload gap between agents.
            - Reasoning: 1-2 sharp sentences, no filler. State WHY this agent, not just WHAT they are.

            Respond ONLY with valid JSON:
            {"agentId":"<id>","confidence":<0.55-0.95>,"reasoning":"<1-2 crisp sentences>"}
            """.formatted(order.getId(), order.getDescription(), agentRoster);
    }

    private RoutingResult parseAndValidate(String raw, List<Agent> agents, Order order, RoutingContext ctx) {
        String cleaned = stripMarkdown(raw);
        JsonNode json;
        try {
            json = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("LLM returned unparseable JSON for order {}: {}", order.getId(), raw);
            return fallback.recommend(order, agents, ctx);
        }

        String suggestedId = json.path("agentId").asText();
        double confidence  = json.path("confidence").asDouble(0.0);
        String reasoning   = json.path("reasoning").asText("No reasoning provided");

        // Guard against hallucinated agentId
        Agent agent = agents.stream()
            .filter(a -> a.getId().equals(suggestedId))
            .findFirst()
            .orElse(null);

        if (agent == null) {
            log.warn("LLM hallucinated agentId '{}' for order {}, falling back", suggestedId, order.getId());
            return fallback.recommend(order, agents, ctx);
        }

        return RoutingResult.builder()
            .recommendedAgent(agent)
            .confidence(confidence)
            .reasoning(reasoning)
            .build();
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
