# ZipRun — AI Reassignment Engine

> Spring Boot 3.3 · React 18 · Groq LLaMA 3.3 70B · Java 17

When a delivery agent goes **OFFLINE**, ZipRun automatically detects all stranded orders, calls an AI to suggest the best available agent for each, and presents ops with one-click Accept / Reject cards — no one clicks a button to start it.

**Live demo:** https://hackathon-frontend-nine.vercel.app  
**Backend API:** https://hackathon-backend-production-6992.up.railway.app

---

## Repository Layout

```
hackathon/
├── hackathon/          # Spring Boot 3.3.5 backend (Java 17, Maven)
├── frontend/           # React 18 + Vite + TypeScript
├── monitoring/         # Prometheus + Grafana config
├── ADR.md              # 5 Architecture Decision Records
├── EXPLANATION.md      # Plain-English problem & solution
├── WALKTHROUGH.md      # Interview Q&A prep (30 questions)
└── ZipRun.postman_collection.json
```

---

## Local Setup — 5 Minutes

### Prerequisites
- Java 17+ · Node 20+ · Maven (bundled via `mvnw`)

### 1. Backend

```bash
cd hackathon/hackathon
./mvnw spring-boot:run
```

> No env vars needed — Groq key has a safe default for local dev.  
> Backend: http://localhost:8080  
> H2 Console: http://localhost:8080/h2-console (JDBC: `jdbc:h2:mem:hackathon`, user: `sa`, pass: blank)

### 2. Frontend

```bash
cd hackathon/frontend
npm install && npm run dev
```

> Frontend: http://localhost:5173

---

## Demo Flow (The Money Shot)

```
1. Open http://localhost:5173 — see 5 agents, 8 assigned orders
2. Click AGT-001 (Priya Sharma) → set OFFLINE
3. Watch: 3 suggestion cards appear automatically within ~2 seconds
4. Each card shows: recommended agent · confidence % · AI reasoning
5. Click Accept on any card → order reassigned, agent counts update
6. Click Reject → system auto-generates a new suggestion (excluding rejected agent)
```

---

## API Reference

No `/api` prefix. All endpoints at root.

### Agents
| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/agents` | All agents with status & load |
| PATCH | `/agents/{id}/status?status=OFFLINE` | **Triggers agentic replan** |
| PATCH | `/agents/{id}/status?status=AVAILABLE` | Bring back online |

### Orders
| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/orders` | All orders |
| GET | `/orders?status=REASSIGNMENT_PENDING` | Stranded orders |
| POST | `/orders` | `{"description":"...", "assignedAgentId":"AGT-002"}` |
| POST | `/orders/{id}/suggest` | On-demand AI suggestion |

### Suggestions
| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/suggestions` | All with AI reasoning |
| PATCH | `/suggestions/{id}` | `{"status":"ACCEPTED"}` or `{"status":"REJECTED"}` |

### AI Assistant
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/assistant` | `{"message":"summarise fleet"}` → natural language ops |

### Health
| Method | Endpoint |
|--------|----------|
| GET | `/actuator/health` |
| GET | `/actuator/metrics` |

---

## Seeded Data

| ID | Name | Status | Orders |
|----|------|--------|--------|
| AGT-001 | Priya Sharma | BUSY | 2 |
| AGT-002 | Rahul Verma | AVAILABLE | 0 |
| AGT-003 | Ananya Iyer | BUSY | 1 |
| AGT-004 | Kiran Nair | AVAILABLE | 0 |
| AGT-005 | Deepak Mehta | BUSY | 3 |

8 orders (ORD-001 → ORD-008) all ASSIGNED on startup.  
Taking AGT-001 OFFLINE → strands ORD-001, ORD-002, ORD-008 → 3 AI suggestions auto-fire.

---

## Architecture

```
PATCH /agents/{id}/status?status=OFFLINE
        │
        ▼
AgentService.updateStatus()
   └─ publishes AgentOfflineEvent ──► returns 200 immediately
                │
                ▼ (Spring @Async thread pool)
        ReplanningService.onAgentOffline()
                │
                ├─ for each stranded order:
                │     ├─ idempotency check (skip if PENDING already exists)
                │     ├─ strategies.get("ai") → AiRoutingStrategy
                │     │     ├─ build recovery prompt (NOT same as initial)
                │     │     ├─ call Groq LLaMA 3.3 70B
                │     │     ├─ validate agentId (hallucination guard)
                │     │     └─ fallback → RuleBasedStrategy on any failure
                │     ├─ optimistic reservation (increment agentCount in DB)
                │     └─ save ReassignmentSuggestion(triggerReason=AGENT_OFFLINE)
                │
                ▼
        React UI (polls every 4s)
           └─ ops sees suggestion cards → Accept / Reject
                     │
                     ▼
              SuggestionService.updateStatus()
                ACCEPTED → order = REASSIGNED, count confirmed
                REJECTED → reservation released, auto-retry with new agent
```

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Routing pattern | `RoutingStrategy` interface + Strategy Pattern | Isolates concern; both callers (HTTP + async) use same contract |
| Runtime switching | `Map<String, RoutingStrategy>` bean map | Spring auto-populates; new strategy = new class only |
| LLM fallback | try/catch → rule-based | Keeps replan alive even when AI is down |
| Async trigger | `ApplicationEventPublisher` + `@Async` | Event-driven (not polling); endpoint returns immediately |
| Routing pool | All non-OFFLINE agents (AVAILABLE preferred, BUSY by load) | Prevents pile-on when multiple agents go offline |
| Capacity reservation | Optimistic increment on suggestion create | Prevents all 3 stranded orders routing to same agent |

Full reasoning in [ADR.md](ADR.md).

---

## Environment Variables (Production)

| Variable | Example | Required |
|----------|---------|----------|
| `LLM_API_KEY` | `gsk_...` | Yes |
| `LLM_PROVIDER` | `groq` | Yes |
| `LLM_MODEL` | `llama-3.3-70b-versatile` | Yes |
| `AI_BASE_URL` | `https://api.groq.com` | Yes |
| `SPRING_PROFILES_ACTIVE` | `prod` | Yes (Railway) |
| `DB_URL` | `jdbc:postgresql://...` | Yes (Railway) |
