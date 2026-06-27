# ZipRun — AI Reassignment Engine

> **Hackathon Submission** | Spring Boot 3.3 · React 18 · Groq LLaMA 70B · Prometheus · Grafana

When a delivery agent goes **OFFLINE**, the system automatically detects all stranded orders, calls an AI to suggest the best available agent for each, and presents the ops team with one-click Accept / Reject cards — all within 3 seconds.

---

## Repository Structure

```
hackathon/
├── hackathon/                  # Spring Boot 3.3.5 backend (Java 17)
│   ├── src/main/java/          # Controllers, Services, Entities, Routing strategies
│   └── src/main/resources/     # application.properties, application-prod.properties
├── frontend/                   # React 18 + Vite + TypeScript dashboard
├── monitoring/                 # Prometheus + Grafana config
├── docker-compose.yml          # Postgres + Prometheus + Grafana (local infra)
├── ADR.md                      # Architecture Decision Records
├── EXPLANATION.md              # Problem & solution in plain English
├── NAVIGATE.md                 # All API URLs + demo flow
├── SETUP.md                    # Railway + Vercel deployment guide
└── ZipRun.postman_collection.json  # Import into Postman for instant API testing
```

---

## Quick Start (Local Dev)

### Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Node.js | 20+ | `node -v` |
| Docker (optional) | any | for Grafana/Prometheus |

### 1. Start backend (IntelliJ or terminal)

```bash
cd hackathon/hackathon
# Add env var: LLM_API_KEY=<your_groq_key>
./mvnw spring-boot:run
```

Backend → **http://localhost:8080**  
H2 Console → **http://localhost:8080/h2-console** (JDBC: `jdbc:h2:mem:hackathon`, user: `sa`, pass: blank)

### 2. Start frontend

```bash
cd hackathon/frontend
npm install
npm run dev
```

Frontend → **http://localhost:5173**

### 3. (Optional) Start monitoring

```bash
docker compose up -d
```

Prometheus → **http://localhost:9090**  
Grafana → **http://localhost:3001** (admin / admin)

---

## Environment Variables

Copy `.env.example` to `.env` and fill in:

```env
LLM_API_KEY=your_groq_key_here
LLM_PROVIDER=groq
LLM_MODEL=llama-3.3-70b-versatile
```

Supported LLM providers: `groq` · `gemini` · `ollama`

---

## API Reference

No `/api` prefix — all endpoints are at root.

### Agents

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/agents` | List all agents with status & order count |
| GET | `/agents/{id}` | Single agent |
| PATCH | `/agents/{id}/status?status=OFFLINE` | Take agent offline → triggers AI replan |
| PATCH | `/agents/{id}/status?status=AVAILABLE` | Bring agent back online |
| PATCH | `/agents/{id}/status?status=BUSY` | Mark agent busy |

### Orders

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/orders` | All orders |
| GET | `/orders?status=REASSIGNMENT_PENDING` | Orders waiting for ops approval |
| POST | `/orders/{id}/suggest` | On-demand AI suggestion for one order |

### Suggestions

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/suggestions` | All suggestions with AI reasoning |
| GET | `/suggestions?status=PENDING` | Pending suggestions only |
| PATCH | `/suggestions/{id}` | Accept or reject `{"status":"ACCEPTED"}` |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | `{"status":"UP"}` |
| GET | `/actuator/prometheus` | Raw Prometheus metrics |

---

## Seeded Demo Data

**5 Agents:**

| ID | Name | Status | Active Orders |
|----|------|--------|---------------|
| AGT-001 | Priya Sharma | BUSY | 2 |
| AGT-002 | Rahul Verma | AVAILABLE | 0 |
| AGT-003 | Ananya Iyer | BUSY | 1 |
| AGT-004 | Kiran Nair | AVAILABLE | 0 |
| AGT-005 | Deepak Mehta | BUSY | 3 |

**8 Orders:** ORD-001 to ORD-008, all ASSIGNED on startup.

Taking AGT-001 OFFLINE strands ORD-001, ORD-002, ORD-008 → 3 AI suggestions auto-generated.

---

## Architecture

```
PATCH /agents/{id}/status?status=OFFLINE
        │
        ▼
AgentService.updateStatus()
   → publishes AgentOfflineEvent (non-blocking, returns 200 immediately)
        │
        ▼ (async background thread)
ReplanningService @EventListener @Async
   → for each stranded order:
        ├── idempotency check (skip if PENDING suggestion already exists)
        ├── AiRoutingStrategy → Groq API → {agentId, confidence, reasoning}
        │      ├── hallucination guard: validate agentId exists
        │      ├── JSON parse guard: catch malformed LLM response
        │      └── fallback: RuleBasedStrategy (min activeOrderCount)
        ├── save ReassignmentSuggestion (triggerReason=AGENT_OFFLINE)
        └── update order status → REASSIGNMENT_PENDING
        │
        ▼
React UI (polling every 4s)
   → ops sees suggestion cards with confidence bar + AI reasoning
   → clicks Accept → order = REASSIGNED, agent counts updated
```

---

## Design Decisions

See [ADR.md](ADR.md) for all 5 Architecture Decision Records covering:
- Routing strategy location (backend vs frontend)
- Runtime strategy switching without restart
- LLM resilience (3-layer fallback)
- Agentic loop trigger mechanism (events vs polling)
- Extension seams for Sprint 2+

---

## Deployment

See [SETUP.md](SETUP.md) for Railway (backend) and Vercel (frontend) step-by-step.

Railway env vars to set:
```
LLM_API_KEY=<groq_key>
LLM_PROVIDER=groq
SPRING_PROFILES_ACTIVE=prod
DB_URL=<railway_postgres_url>
```
