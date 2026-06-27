package com.example.hackathon.config;

import com.example.hackathon.entity.Agent;
import com.example.hackathon.entity.Order;
import com.example.hackathon.repository.AgentRepository;
import com.example.hackathon.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    @Bean
    ApplicationRunner seed(AgentRepository agentRepo, OrderRepository orderRepo) {
        return args -> {
            if (agentRepo.count() > 0) return;

            // 5 agents — from Addendum A
            Agent a1 = agent("AGT-001", "Priya Sharma",  2, Agent.AgentStatus.BUSY);
            Agent a2 = agent("AGT-002", "Rahul Verma",   0, Agent.AgentStatus.AVAILABLE);
            Agent a3 = agent("AGT-003", "Ananya Iyer",   1, Agent.AgentStatus.BUSY);
            Agent a4 = agent("AGT-004", "Kiran Nair",    0, Agent.AgentStatus.AVAILABLE);
            Agent a5 = agent("AGT-005", "Deepak Mehta",  3, Agent.AgentStatus.BUSY);
            agentRepo.saveAll(List.of(a1, a2, a3, a4, a5));

            // 8 orders — all ASSIGNED, spread across agents
            orderRepo.saveAll(List.of(
                order("ORD-001", "Electronics — Koramangala to Indiranagar",   a1),
                order("ORD-002", "Groceries — HSR Layout to BTM",              a1),
                order("ORD-003", "Pharma — Whitefield to Marathahalli",        a3),
                order("ORD-004", "Documents — MG Road to Jayanagar",           a5),
                order("ORD-005", "Food — Bellandur to Electronic City",        a5),
                order("ORD-006", "Apparel — Malleshwaram to Rajajinagar",      a5),
                order("ORD-007", "Books — Banashankari to JP Nagar",           a3),
                order("ORD-008", "Hardware — Peenya to Yeshwanthpur",         a1)
            ));

            log.info("Seeded 5 agents and 8 orders");
        };
    }

    private Agent agent(String id, String name, int orderCount, Agent.AgentStatus status) {
        Agent a = new Agent();
        a.setId(id);
        a.setName(name);
        a.setActiveOrderCount(orderCount);
        a.setStatus(status);
        return a;
    }

    private Order order(String id, String description, Agent agent) {
        Order o = new Order();
        o.setId(id);
        o.setDescription(description);
        o.setAssignedAgent(agent);
        o.setStatus(Order.OrderStatus.ASSIGNED);
        return o;
    }
}
