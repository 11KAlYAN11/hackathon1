package com.example.hackathon.event;

import com.example.hackathon.entity.Agent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class AgentOfflineEvent extends ApplicationEvent {

    private final Agent agent;
    private final List<String> affectedOrderIds;

    public AgentOfflineEvent(Object source, Agent agent, List<String> affectedOrderIds) {
        super(source);
        this.agent = agent;
        this.affectedOrderIds = affectedOrderIds;
    }
}
