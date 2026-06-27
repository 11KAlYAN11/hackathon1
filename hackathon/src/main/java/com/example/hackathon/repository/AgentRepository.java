package com.example.hackathon.repository;

import com.example.hackathon.entity.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRepository extends JpaRepository<Agent, String> {
    List<Agent> findByStatus(Agent.AgentStatus status);
}
