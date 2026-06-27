package com.example.hackathon.controller;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public List<Agent> getAll() {
        return agentService.getAll();
    }

    @PatchMapping("/{id}/status")
    public Agent updateStatus(@PathVariable String id, @RequestParam Agent.AgentStatus status) {
        return agentService.updateStatus(id, status);
    }
}
