# Walkthrough Prep — 30 Q&A

ZipRun AI Reassignment Engine · Spring Boot + System Design

---

## SECTION 1 — The Agentic Loop (they will trace this)

**Q1. Walk me through what happens when I hit `PATCH /agents/AGT-001/status?status=OFFLINE`.**

`AgentService.updateStatus()` saves the agent as OFFLINE, then calls `publisher.publishEvent(new AgentOfflineEvent(...))` and returns 200 immediately. In a background thread, `ReplanningService.onAgentOffline()` picks it up (it's `@EventListener @Async`), finds all ASSIGNED/REASSIGNED/REASSIGNMENT_PENDING orders for that agent, and for each order: checks idempotency → runs the active routing strategy → saves a `ReassignmentSuggestion` with `triggerReason=AGENT_OFFLINE` → sets order to `REASSIGNMENT_PENDING`. The UI picks it up on the next 4-second poll.

---

**Q2. Why `ApplicationEventPublisher` and not a scheduled poller?**

A poller fires on a timer — it's automated, not agentic. The loop should fire because something changed (agent went OFFLINE), not because 5 seconds passed. `ApplicationEventPublisher` is event-driven, keeps the endpoint non-blocking, and Spring manages the thread pool and lifecycle. It's also the pattern the problem brief explicitly recommended.

---

**Q3. What makes this system "agentic"?**

Four things: **Observe** (OFFLINE event fires), **Reason** (which specific orders are stranded?), **Act** (queue suggestions — not auto-assign), **Checkpoint** (ops approves). The trigger is a state change not a timer. The action is bounded — it proposes, doesn't decide. The human is kept in the loop at the irreversible step.

---

**Q4. How does idempotency work — what if the same agent goes offline twice?**

Before creating a new suggestion, `ReplanningService` checks: `existsByOrderAndStatusPendingAndTriggerAgentOffline()`. If a PENDING + AGENT_OFFLINE suggestion already exists for that order, it skips. Same agent offline twice → no duplicate suggestions.

---

**Q5. When would you remove the human checkpoint (auto-assign)?**

If confidence score > 0.95 AND no other active suggestions AND SLA breach is imminent. But that's a trust model decision, not a sprint 1 requirement. The current design deliberately keeps ops in the loop — auto-assignment is not "more complete," it's a different system.

---

## SECTION 2 — Routing & Strategy Pattern

**Q6. What design pattern is the routing engine?**

**Strategy Pattern.** `RoutingStrategy` interface with two implementations: `RuleBasedStrategy` and `AiRoutingStrategy`. The active strategy is selected at runtime from a `Map<String, RoutingStrategy>` bean map. The callers (`OrderService`, `ReplanningService`) depend only on the interface.

---

**Q7. How do you switch from AI to rule-based at runtime without restarting?**

Set env var `ROUTING_STRATEGY=rule-based`. The `Map<String, RoutingStrategy>` is injected by Spring, keyed by bean name. At every call, `strategies.get(activeStrategy)` reads the current value — no restart needed. The `@Value("${routing.strategy:ai}")` is re-read per request.

---

**Q8. How would you add `ZoneAffinityStrategy` for sprint 2?**

1. `class ZoneAffinityStrategy implements RoutingStrategy`
2. `@Component("zone-affinity")`
3. Set `ROUTING_STRATEGY=zone-affinity`

Zero existing code changes. That's the open/closed principle — open for extension, closed for modification.

---

**Q9. Why is the routing contract called from two places and why does that matter?**

HTTP endpoint (`POST /orders/{id}/suggest`) and the async event listener (`ReplanningService`). Both callers use the same `RoutingStrategy` interface — no duplication, no divergence. If you had put routing logic inside `OrderService`, the async path would need to duplicate or reach into it.

---

**Q10. What does `RoutingResult` contain and why is it a typed object not raw values?**

`RoutingResult` = `{ Agent recommendedAgent, double confidence, String reasoning }`. Typed to avoid primitive obsession and to make the interface contract explicit. Both strategies return the same shape — the caller doesn't care which strategy ran.

---

## SECTION 3 — LLM & AI Integration

**Q11. What does the AI prompt contain for an initial suggestion vs a re-plan?**

**Initial:** order description, full agent roster with status and load.

**Re-plan (recovery prompt):** adds — which agent went OFFLINE, how many orders are stranded, that this is a recovery situation, explicitly excludes the offline agent from candidates. Different context = model reasons differently. They are not the same prompt with a field added.

---

**Q12. What happens if the LLM returns an agent ID that doesn't exist?**

Hallucination guard: the returned `agentId` is validated against the actual agent list passed into the strategy. If it's not in the list, it falls back to `RuleBasedStrategy`. LLMs occasionally make up identifiers — this is always checked.

---

**Q13. What happens if the LLM call times out or returns malformed JSON?**

`callLLM()` throws. `AiRoutingStrategy.recommend()` wraps the entire call in try/catch. Any exception → falls back to `RuleBasedStrategy` and logs at WARN with order ID and failure reason. The suggestion is still created — never a silent drop.

---

**Q14. Why Groq LLaMA 3.3 70B and not GPT-4?**

Free tier, extremely fast (low latency for sync-ish use), and 70B is strong enough for structured JSON output with agent reasoning. The `LLMGateway` is provider-agnostic — switching to Gemini or Ollama is an env var change (`LLM_PROVIDER`, `LLM_MODEL`, `AI_BASE_URL`).

---

**Q15. Where is the LLM called — on the request thread or off it?**

In the agentic re-plan path: off the request thread (inside `@Async`). In the on-demand path (`POST /orders/{id}/suggest`): on the request thread, but this is an explicit ops action and acceptable latency. The brief says "keep AI calls off the critical path" — the critical path is the PATCH status endpoint, which returns before any LLM call starts.

---

## SECTION 4 — Spring Boot & Java

**Q16. What is `@Async` and what thread pool does it use?**

`@Async` tells Spring to run the method in a separate thread from Spring's `TaskExecutor` thread pool. Requires `@EnableAsync` on a config class. The calling thread doesn't wait — fire and forget. Exceptions in async methods don't propagate to the caller; they're logged.

---

**Q17. What is `ApplicationEventPublisher` and why not a direct method call?**

`ApplicationEventPublisher.publishEvent()` decouples the publisher from the listener. `AgentService` doesn't need to know about `ReplanningService`. Direct method call would create a hard dependency and force the endpoint to wait for re-planning. Events also let you add more listeners in future (e.g., send a Slack alert) without touching `AgentService`.

---

**Q18. What is `@Transactional` doing in your services?**

Wraps DB operations in a transaction — all reads/writes either commit together or roll back. Important in `SuggestionService.updateStatus()` where we update the suggestion status, update the agent count, and update the order status — all must succeed or all roll back.

---

**Q19. What is Spring Data JPA and what's a repository?**

Spring Data JPA generates SQL from method names. `AgentRepository extends JpaRepository<Agent, String>` gives you `findAll()`, `findById()`, `save()`, `delete()` for free. Custom queries like `findByStatus(AVAILABLE)` are inferred from the method name.

---

**Q20. How does H2 vs PostgreSQL work in this project?**

`application.properties` uses H2 in-memory for local dev — no DB setup needed, starts fresh every restart. `application-prod.properties` switches to PostgreSQL with the `DB_URL` env var. Activated by `SPRING_PROFILES_ACTIVE=prod` on Railway.

---

**Q21. What is `@EventListener` and `@Async` together — any gotcha?**

`@EventListener` registers the method as a listener for a specific event type. `@Async` runs it in a thread pool. The gotcha: if the listener is called within the same transaction as the publisher, `@Async` may lose transaction context. We handle this by publishing the event after the `save()` and relying on a fresh transaction in the listener.

---

**Q22. What HTTP status codes does your API return?**

- `200` — successful GET, PATCH
- `201` — POST (order/suggestion created)
- `400` — bad request (invalid status value)
- `404` — agent/order/suggestion not found
- `500` — unexpected server error

---

## SECTION 5 — System Design

**Q23. How would you scale this if there were 10,000 agents going offline simultaneously?**

`ApplicationEventPublisher` with `@Async` uses a bounded thread pool — it would queue up. For true scale: replace with Kafka. Each OFFLINE event becomes a Kafka message; `ReplanningService` becomes a consumer group with multiple instances. The routing logic stays identical — you're just changing the transport.

---

**Q24. What's the N+1 query problem and did you run into it?**

N+1 = fetching 1 list, then 1 more query per item to get related data. In our case, loading suggestions and then loading the associated Order and Agent for each would be N+1. We mitigate with JPA `@ManyToOne` with `EAGER` fetch for the core relationships, and `findAll()` with `@EntityGraph` where needed.

---

**Q25. Why is the routing pool all non-OFFLINE agents, not just AVAILABLE?**

If only AVAILABLE agents are candidates and 3 agents go OFFLINE simultaneously leaving 1 AVAILABLE agent, all 5 stranded orders route to that 1 agent — defeating the purpose. BUSY agents with capacity are valid candidates. Pool = all non-OFFLINE agents, AVAILABLE preferred, BUSY sorted by `activeOrderCount` ascending.

---

**Q26. What is optimistic capacity reservation and why do you need it?**

When the async loop generates a suggestion for Agent X for Order 1, it immediately increments X's `activeOrderCount` in the DB. When Order 2 is processed 50ms later, X appears "partially loaded" and a different agent is preferred. Without this, all 3 orders in the same loop run recommend the same agent because the DB hasn't updated yet (eventual consistency problem within one async batch).

---

**Q27. What happens when ops rejects a suggestion?**

`SuggestionService.updateStatus(REJECTED)`:
1. Suggestion → REJECTED
2. Agent's `activeOrderCount` decremented (release the reservation)
3. `autoRetry()` called — runs routing again excluding the rejected agent
4. New `PENDING` suggestion created with `[Retry after rejection]` prefix
5. Order stays in `REASSIGNMENT_PENDING` — never gets stuck with no active suggestion

---

**Q28. How does the frontend know new suggestions appeared?**

Polling every 4 seconds via `setInterval` in React. `GET /suggestions` returns all suggestions; the UI filters for `PENDING`. Simple and reliable — no WebSocket needed for this scale. 4s is fast enough for ops response time.

---

**Q29. What's the difference between REASSIGNED and REASSIGNMENT_PENDING?**

`REASSIGNMENT_PENDING` = order has a PENDING suggestion waiting for ops to approve. `REASSIGNED` = suggestion was ACCEPTED, order is now assigned to the new agent. `ASSIGNED` = original state from morning shift. State machine: `ASSIGNED → REASSIGNMENT_PENDING → REASSIGNED → DELIVERED`.

---

**Q30. If you had 2 more hours, what would you add?**

1. **SSE streaming** — token-by-token AI reasoning in the UI (the brief's bonus)
2. **SLA deadline field on Order** — `slaDeadline: Instant`, nullable, activated in sprint 3 when the proactive loop timer fires
3. **Agent load visualization** — bar chart per agent showing orders vs capacity (already have the data, just need the UI component)

I deliberately didn't build these because completing the agentic loop correctly (idempotency, optimistic reservation, auto-retry, fallback) was the correctness requirement. These are visibility enhancements.

---

## Quick Reference — Key Classes

| Class | Package | Responsibility |
|-------|---------|----------------|
| `AgentService` | `service` | Status updates, publishes `AgentOfflineEvent` |
| `ReplanningService` | `service` | `@Async` listener, orchestrates replan loop |
| `SuggestionService` | `service` | Accept/reject logic, optimistic reservation, auto-retry |
| `OrderService` | `service` | Order creation, assignment |
| `AiRoutingStrategy` | `routing` | LLM call, hallucination guard, fallback |
| `RuleBasedStrategy` | `routing` | Min activeOrderCount, always available |
| `LLMGateway` | `llm` | HTTP wire format for Groq/Gemini/Ollama |
| `AssistantService` | `service` | Natural language ops assistant |
| `WebConfig` | `config` | CORS for Vercel + Railway |
