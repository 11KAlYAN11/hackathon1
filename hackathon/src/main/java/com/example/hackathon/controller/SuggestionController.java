package com.example.hackathon.controller;

import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping
    public List<ReassignmentSuggestion> getAll() {
        return suggestionService.getAll();
    }

    @PatchMapping("/{id}")
    public ReassignmentSuggestion updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        ReassignmentSuggestion.SuggestionStatus status =
            ReassignmentSuggestion.SuggestionStatus.valueOf(body.get("status"));
        return suggestionService.updateStatus(id, status);
    }
}
