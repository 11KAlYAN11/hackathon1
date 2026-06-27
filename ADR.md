# Architecture Decision Record — AI Reassignment Engine

---

## ADR-1: Where does routing logic live?

**Context**
The system needs to pick the best available agent for a given order. This logic could live in the controller, the service layer, or a dedicated component. The routing contract is called from two places: an HTTP endpoint (`POST /orders/{id}/suggest`) and an async event handler (the agentic re-planning loop). Sprint 2 adds a third strategy (`ZoneAffinityStrategy`).

**Options considered**
- (a) Put logic directly in `OrderService` — simple but mixes routing concern with persistence and event orchestration; service grows unbounded.
- (b) Put logic in `OrderController` — violates separation of concerns; controllers should delegate.
- (c) Dedicated `RoutingStrategy` interface with separate implementations — isolates the routing concern; both callers use the same contract; new strategies add a class without modifying existing ones.

**Decision**
Option (c) — a `RoutingStrategy` interface in its own `routing` package. `OrderService` and `ReplanningService` both depend on the interface, not any concrete implementation. The interface takes `(Order, List<Agent>, RoutingContext)` and returns `RoutingResult` — a typed value object that carries agent, confidence, and reasoning.

**Tradeoffs accepted**
Slightly more classes than embedding logic in a service. In return: routing is independently testable, both callers need zero changes when a strategy is swapped, and sprint 2's `ZoneAffinityStrategy` is an additive change (new class + `@Component("zone-affinity")` annotation — nothing else touches).

---

## ADR-2: How does runtime strategy switching work?

**Context**
The active routing strategy must be switchable via config at runtime — no code change, no restart. The strategy is called from both an HTTP endpoint (synchronous) and an async event listener, so the selection mechanism must work cleanly from both call paths.

**Options considered**
- (a) `@Qualifier` + a config property — requires restart to switch; each call site needs a conditional.
- (b) Auto-wired `Map<String, RoutingStrategy>` bean map — Spring populates the map keyed by bean name (`"ai"`, `"rule-based"`); active strategy is selected at call time by reading `routing.strategy` from config.
- (c) Manual factory with a switch statement — explicit but requires modifying the factory when new strategies are added.

**Decision**
Option (b) — Spring auto-wires `Map<String, RoutingStrategy>` into any bean that declares it. Both `OrderService` and `ReplanningService` inject this map and call `strategies.getOrDefault(activeStrategy, strategies.get("rule-based"))`. The value of `routing.strategy` can be changed via environment variable (`ROUTING_STRATEGY=rule-based`) and takes effect on the next request — no restart needed.

Adding `ZoneAffinityStrategy` in sprint 2: implement `RoutingStrategy`, annotate with `@Component("zone-affinity")`, set `ROUTING_STRATEGY=zone-affinity`. Zero existing code changes.

**Tradeoffs accepted**
The map approach is slightly less explicit than a factory — a reader needs to know Spring populates the map by bean name. Misconfigured `routing.strategy` fails at runtime rather than compile time. Mitigated by logging the active strategy name on every routing call.

---

## ADR-3: How does the system stay resilient when the LLM is unavailable?

**Context**
LLM calls fail in several distinct ways: network timeout, quota exhaustion, malformed JSON response, hallucinated agent identifiers that don't exist in the database. The async re-plan path is especially sensitive — a failed AI call must still produce a suggestion rather than silently dropping the re-plan.

**Options considered**
- (a) Let exceptions propagate — caller sees a 502 or async task dies silently. Unacceptable for a reassignment path.
- (b) Circuit breaker (Resilience4j) — adds a dependency; more than needed for this scope.
- (c) Try/catch with explicit fallback to rule-based strategy — lightweight, predictable, already have rule-based as a reliable fallback.

**Decision**
Option (c) — `AiRoutingStrategy.recommend()` wraps the entire LLM call in a try/catch. Specific failure modes handled:
- **Network/timeout/quota**: caught as `RuntimeException`, falls back to rule-based.
- **Malformed JSON**: `objectMapper.readTree()` throws, caught, falls back.
- **Hallucinated agentId**: validated against the agent list passed in — if the returned ID isn't in the list, falls back. The agent list comes from the DB so only real IDs are valid.
- **Async re-plan failure**: `ReplanningService.route()` has its own try/catch — any strategy failure produces a rule-based suggestion rather than skipping the order.

All fallback events are logged at WARN with the order ID and failure reason.

**Tradeoffs accepted**
We do not retry failed LLM calls — a timeout already means the user is waiting; retrying compounds it. Rule-based suggestions are accurate enough for ops to act on. If AI is degraded, ops sees slightly less rich reasoning but the system keeps functioning.

---

## ADR-4: How is the agentic loop triggered and kept off the request path?

**Context**
`PATCH /agents/{id}/status` must return immediately. When an agent goes OFFLINE, identifying affected orders and running routing for each can take seconds (especially with AI). Blocking the HTTP response on this work would make the endpoint feel broken.

**Options considered**
- (a) Scheduled poller — checks for offline agents every N seconds. Not agentic; fires on a timer not a state change; unnecessary latency between agent going offline and suggestions appearing.
- (b) Separate thread via `CompletableFuture.runAsync()` — works but no Spring lifecycle management; exceptions are hard to observe.
- (c) `ApplicationEventPublisher` + `@EventListener` + `@Async` — decouples the status update from re-planning; Spring manages the thread pool; event carries the agent and affected order IDs; fully observable via standard Spring logging.

**Decision**
Option (c). `AgentService.updateStatus()` publishes an `AgentOfflineEvent` (containing the agent and list of affected order IDs) via `ApplicationEventPublisher`. `ReplanningService.onAgentOffline()` is annotated `@EventListener` + `@Async`, so it runs in Spring's async thread pool — the PATCH endpoint returns before re-planning starts.

Idempotency: before creating a suggestion, `ReplanningService` checks for an existing `PENDING` + `AGENT_OFFLINE` suggestion for that order. If one exists, it skips — the same agent going offline twice won't produce duplicate suggestions.

Failure handling: `ReplanningService.processOrder()` catches any routing failure and falls back to rule-based — async re-plan failures never produce a silent drop.

**Tradeoffs accepted**
Async means the suggestions appear a moment after the PATCH response — the UI polls and picks them up on the next tick. If the JVM crashes mid-replan, in-progress suggestions are lost (no persistent queue). Acceptable for this scope; sprint 3 could add a durable queue if needed.

---

## ADR-5: What was designed to extend, and what was deliberately deferred?

**Context**
Sprint 2 adds zone awareness, capacity constraints, and weight classes. Sprint 3 adds SLA-deadline-triggered re-planning and a full dispatch board. Today's decisions either welcome or resist those additions.

**Extension seams in the current code**

*Sprint 2 — ZoneAffinityStrategy:*
`Agent` has nullable `currentZone` and `maxCapacity` fields already in the schema — no migration needed, just activate them. `RoutingContext` can carry zone data. The new strategy implements `RoutingStrategy` and registers as `@Component("zone-affinity")`. Zero existing code changes.

*Sprint 3 — SLA-triggered proactive loop:*
`Order` has a nullable `slaDeadline` field. `AgentOfflineEvent` is a specific event type — but the async listener pattern is general. Sprint 3 adds an `SlaBreachEvent` published by a scheduler; `ReplanningService` adds a second `@EventListener` for it. The routing interface is already comfortable being called from a scheduler context — it takes order + agents + context, with no HTTP-specific coupling.

**Additional runtime decisions**

*Routing pool includes BUSY agents:* The initial implementation only considered AVAILABLE agents as candidates. Under load (multiple agents OFFLINE simultaneously), this caused all stranded orders to be routed to the single AVAILABLE agent. Changed to include BUSY agents in the candidate pool — the strategy now prefers AVAILABLE first, then BUSY sorted by active order count. This spreads load across the fleet rather than piling onto one agent.

*Optimistic capacity reservation:* When the agentic loop generates a suggestion for Agent X, it immediately increments X's activeOrderCount in the DB. Subsequent orders in the same loop see X as "spoken for" and route to less-loaded agents. On REJECT, the reservation is released. On ACCEPT, the count is already correct — no double-increment. This prevents the "all 4 stranded orders recommend the same agent" pattern.

*Auto-retry on suggestion rejection:* When ops rejects a suggestion, the system immediately generates a new PENDING suggestion (excluding the rejected agent from the candidate pool). The order never gets permanently stuck in REASSIGNMENT_PENDING with no active suggestion.

**Deliberately deferred**

*Full dispatch board (T5 ceiling):* Deferred in favour of completing the agentic loop correctly. The re-plan badge appearing after an agent goes offline is the core correctness requirement. The full dispatch board is a visibility enhancement. A clean floor that proves the loop works scores more in the rubric than a board built on a weaker backend.

*SSE token streaming (bonus T3):* Deferred — adds Flux/WebFlux complexity. The core AI integration with structured JSON output, fallback, and hallucination guard is more valuable to complete cleanly.

*Auto-assignment on replan:* Explicitly not built. The system queues suggestions for ops to approve. Auto-assignment is a different system with a different trust model — it is not "more complete," it is a misunderstanding of the requirement.

*Kafka/persistent event queue:* Not added. `ApplicationEventPublisher + @Async` is sufficient for this scope and is the pattern the brief itself recommends. Kafka would add operational complexity with no additional correctness benefit within the hackathon scope. If the JVM crashes mid-replan, suggestions are lost — this is acceptable and documented; sprint 3 could add a durable queue.
