package com.example.hackathon.routing;

import com.example.hackathon.entity.Agent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoutingResult {
    private Agent recommendedAgent;
    private double confidence;
    private String reasoning;
}
