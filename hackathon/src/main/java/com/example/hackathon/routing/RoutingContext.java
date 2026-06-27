package com.example.hackathon.routing;

import com.example.hackathon.entity.ReassignmentSuggestion;
import lombok.Builder;
import lombok.Data;

/**
 * Context passed to every strategy call.
 * Tells the strategy WHY routing is happening — initial assignment or recovery.
 * The AI strategy uses this to construct different prompts for each situation.
 */
@Data
@Builder
public class RoutingContext {
    private ReassignmentSuggestion.TriggerReason triggerReason;
    private String offlineAgentId;   // set when triggerReason = AGENT_OFFLINE
    private int totalStranded;       // how many orders lost their agent (for AI replan prompt)
}
