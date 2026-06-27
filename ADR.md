# Architecture Decision Records — ZipRun AI Reassignment Engine

---

## ADR-1: Where does routing logic live?

```
❌ Option A — Inside OrderService          ❌ Option B — Inside Controller
   OrderService {                              OrderController {
     route() ← routing                           route() ← WRONG LAYER
     save()  ← persistence                    }
     publish() ← events
   }  ← grows unbounded

✅ Chose Option C — Dedicated RoutingStrategy interface

   ┌─────────────────────────────────────────┐
   │          «interface»                    │
   │         RoutingStrategy                 │
   │  recommend(Order, List<Agent>,          │
   │            RoutingContext)              │
   │  → RoutingResult                        │
   └────────┬──────────────┬────────────────┘
            │              │
   ┌─────────┴──┐    ┌──────┴──────────┐
   │ RuleBased  │    │ AiRouting       │
   │ Strategy   │    │ Strategy        │
   │            │    │                 │
   │ min(active │    │ Groq LLaMA call │
   │ OrderCount)│    │ + fallback      │
   └────────────┘    └─────────────────┘
        ▲                   ▲
        └──── both callers use same interface ────┘
              │                          │
     OrderService                 ReplanningService
     (HTTP path)                  (Async @EventListener)
```

**Decision:** `RoutingStrategy` interface in its own `routing/` package. Both callers depend on the interface, never a concrete class.

**Tradeoff:** More classes. Gain: routing is independently testable; sprint 2's `ZoneAffinityStrategy` = new class + `@Component("zone-affinity")`, zero existing code changes.

---

## ADR-2: Runtime strategy switching without restart

```
ROUTING_STRATEGY=ai          (env var, no restart needed)
         │
         ▼
┌────────────────────────────────────────────────┐
│  Spring auto-wires Map<String, RoutingStrategy> │
│                                                │
│  {                                             │
│    "ai"          → AiRoutingStrategy           │
│    "rule-based"  → RuleBasedStrategy           │
│    "zone-affinity" → ZoneAffinityStrategy ← sprint 2, just add class
│  }                                             │
└────────────────────────────────────────────────┘
         │
         ▼
  strategies.getOrDefault(
      activeStrategy,           ← reads env var at call time
      strategies.get("rule-based")  ← safe fallback
  )
```

**How to add sprint 2's ZoneAffinityStrategy:**
1. `class ZoneAffinityStrategy implements RoutingStrategy`
2. `@Component("zone-affinity")`
3. `ROUTING_STRATEGY=zone-affinity`
4. **Zero existing code changes.**

**Tradeoff:** Misconfigured strategy name fails at runtime, not compile time. Mitigated: strategy name logged on every routing call.

---

## ADR-3: LLM resilience — what happens when AI fails

```
AiRoutingStrategy.recommend()
        │
        ├─ try: callLLM(prompt)
        │         │
        │         ├─ Network timeout ──────────────┐
        │         ├─ 401 / quota error ────────────┤
        │         ├─ Malformed JSON ───────────────┤  all caught
        │         └─ Hallucinated agentId ─────────┤
        │                    │                     │
        │         validate returned agentId        │
        │         against real agent list ─────────┘
        │                                          │
        └─ catch(Exception e)                      │
              log.warn(orderId, failure reason) ◄──┘
              fallback → RuleBasedStrategy
                         (always available, no I/O)

ReplanningService (async path):
        │
        ├─ try: active strategy
        └─ catch → RuleBasedStrategy
              ← async replan NEVER produces silent drop
```

**Decision:** try/catch with rule-based fallback. No Resilience4j — too heavy for this scope; we already have a reliable in-process fallback.

**Tradeoff:** No retry on LLM timeout. A timeout already means the user waits; retrying compounds it. Rule-based suggestions are accurate enough for ops to act on.

---

## ADR-4: Agentic loop — kept off the request path

```
❌ Option A — Scheduled poller
   @Scheduled(fixedDelay=5000)      ← fires on timer, not state change
   checkForOfflineAgents()           ← not agentic; unnecessary latency

❌ Option B — CompletableFuture.runAsync()
   runAsync(() -> replan())          ← works but no Spring lifecycle;
                                        exceptions hard to observe

✅ Option C — ApplicationEventPublisher + @EventListener + @Async

PATCH /agents/{id}/status
   │
   ▼
AgentService.updateStatus()
   ├─ save agent (OFFLINE)
   ├─ publisher.publishEvent(new AgentOfflineEvent(agent, affectedOrders))
   └─ return 200 ◄─── request ends here, no waiting

           │ (Spring async thread pool)
           ▼
ReplanningService.onAgentOffline()   @EventListener @Async
   │
   ├─ for each stranded order:
   │     ├─ idempotency: existsByOrderAndStatusAndTrigger(PENDING, AGENT_OFFLINE)?
   │     │     └─ skip if true (same agent offline twice = no duplicates)
   │     ├─ route → suggest → save
   │     └─ order.status = REASSIGNMENT_PENDING
   │
   └─ suggestions appear on UI next poll (4s)
```

**Tradeoff:** Suggestions appear ~1-4s after PATCH response (poll cycle). If JVM crashes mid-replan, in-flight suggestions are lost. Acceptable for this scope; sprint 3 could add a durable queue (Kafka/Redis).

---

## ADR-5: What was built to extend, what was deferred

```
EXTENSION SEAMS IN CURRENT CODE
────────────────────────────────

Sprint 2 — Zone & Capacity (nullable fields, zero migration):
  Agent  { currentZone: String?,  maxCapacity: Integer? }  ← activate, not add
  Order  { pickupZone: String?,   dropoffZone: String?,
           weightClass: String?,  slaDeadline: Instant? }  ← same

Sprint 2 — ZoneAffinityStrategy:
  implement RoutingStrategy → @Component("zone-affinity") → done

Sprint 3 — SLA-triggered proactive loop:
  Order.slaDeadline already in schema
  @Scheduled monitor publishes SlaBreachEvent
  ReplanningService adds @EventListener for SlaBreachEvent
  ← same routing interface, same suggestion flow, new trigger only


DELIBERATELY NOT BUILT
───────────────────────

  ✗ Full dispatch board (UI ceiling)
      → agentic loop correctness > visibility enhancement
      → clean floor + working replan > ambitious board with weaker backend

  ✗ SSE token streaming (+5 bonus)
      → adds WebFlux/Flux complexity
      → structured JSON output + fallback + hallucination guard more valuable to get right

  ✗ Auto-assignment on replan
      → not "more complete" — it's a different trust model
      → system proposes, ops disposes — that's the requirement

  ✗ Kafka persistent event queue
      → ApplicationEventPublisher + @Async is what the brief recommends
      → no additional correctness benefit within hackathon scope
      → sprint 3 can add durable queue if JVM-crash durability is required


RUNTIME DECISIONS (added during build)
────────────────────────────────────────

  + Routing pool includes BUSY agents (not AVAILABLE-only)
      Initial impl routed all stranded orders to the single AVAILABLE agent.
      Fixed: pool = all non-OFFLINE agents, AVAILABLE preferred, BUSY sorted by load.

  + Optimistic capacity reservation
      Suggestion creation → increment agentCount in DB immediately.
      Prevents orders 2/3/4 all routing to same "free" agent.
      REJECT → release reservation. ACCEPT → count already correct.

  + Auto-retry on rejection
      Ops rejects → system generates new PENDING suggestion (excluding rejected agent).
      Order never gets permanently stuck with no active suggestion.
```
