package com.example.hackathon.controller;

import com.example.hackathon.entity.Order;
import com.example.hackathon.entity.ReassignmentSuggestion;
import com.example.hackathon.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<Order> getAll(@RequestParam(required = false) Order.OrderStatus status) {
        return orderService.getAll(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@RequestBody Order order) {
        return orderService.create(order);
    }

    @PostMapping("/{id}/suggest")
    public ReassignmentSuggestion suggest(@PathVariable String id) {
        return orderService.suggest(id);
    }
}
