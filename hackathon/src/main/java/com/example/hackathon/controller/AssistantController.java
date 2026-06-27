package com.example.hackathon.controller;

import com.example.hackathon.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    public record ChatRequest(String message) {}

    @PostMapping
    public AssistantService.AssistantResponse chat(@RequestBody ChatRequest req) {
        return assistantService.chat(req.message());
    }
}
