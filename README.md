# Hackathon — Agentic Ticket Routing System

A Spring Boot 3 + React (Vite) application with LLM-powered agent routing, real-time SSE events, and full observability via Prometheus + Grafana.

## Repository Structure

```
hackathon/
├── hackathon/          # Spring Boot backend
├── frontend/           # React 18 + Vite frontend
├── monitoring/         # Prometheus + Grafana config
├── docker-compose.yml  # Local Postgres + Prometheus + Grafana
└── .env.example        # Environment variable template
```

## Quick Start (Local)

### 1. Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20+ | `node -v` |
| Docker | any | `docker --version` |

### 2. Start infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL (5432), Prometheus (9090), and Grafana (3001).

### 3. Start backend

```bash
cd hackathon
export LLM_API_KEY=your_groq_key_here
./mvnw spring-boot:run
```

Backend runs on `http://localhost:8080`

### 4. Start frontend

```bash
cd frontend
cp .env.example .env.local   # already done — VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agents` | List all agents |
| GET | `/api/agents/online` | List online agents only |
| POST | `/api/agents` | Create agent |
| PATCH | `/api/agents/{agentId}/status?status=OFFLINE` | Update agent status |
| GET | `/api/tickets` | List all tickets |
| GET | `/api/tickets/{id}` | Get ticket by ID |
| POST | `/api/tickets` | Create ticket (triggers LLM routing) |
| POST | `/api/tickets/{id}/suggestions/{sid}/accept` | Accept routing suggestion |
| POST | `/api/tickets/{id}/replan` | Re-run routing (agent went offline) |
| GET | `/api/events` | SSE stream (agent-offline, replan, ticket-assigned) |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

## LLM Routing

On ticket creation the system automatically:
1. Fetches all online agents and their skills
2. Sends a structured prompt to the LLM
3. Parses the response: `{"agentId":"...","confidence":0.85,"reasoning":"..."}`
4. Persists the suggestion — **ops must accept it, it is never auto-assigned**
5. If the suggested agent goes offline, call `/replan` to get a new suggestion

Supported providers (set via `LLM_PROVIDER` env var):

| Provider | `LLM_PROVIDER` | Free tier |
|----------|---------------|-----------|
| Groq + Llama 3.3 | `groq` | Yes |
| Gemini 1.5 Flash | `gemini` | Yes |
| Ollama (local) | `ollama` | No API key needed |
| OpenAI | `openai` | No |

## Real-time Events (SSE)

Connect to `GET /api/events` (text/event-stream). Events emitted:

| Event name | Data | When |
|------------|------|------|
| `agent-offline` | `agentId` | Agent status → OFFLINE |
| `agent-online` | `agentId` | Agent status → ONLINE |
| `replan` | `ticketId:agentId` | Replan triggered |
| `ticket-assigned` | `ticketId:agentId` | Suggestion accepted |

## Seeded Data

On startup 5 agents are seeded automatically:

| agentId | Name | Skills |
|---------|------|--------|
| agent-001 | Alice Kumar | billing, payments, refunds |
| agent-002 | Bob Mensah | technical, debugging, api |
| agent-003 | Carol Zhang | onboarding, account, kyc |
| agent-004 | David Osei | escalation, legal, compliance |
| agent-005 | Eva Petrov | billing, technical, general |

## Observability

| URL | Tool |
|-----|------|
| `http://localhost:9090` | Prometheus |
| `http://localhost:3001` | Grafana (admin/admin) |
| `http://localhost:8080/actuator/prometheus` | Raw metrics |
| `http://localhost:8080/h2-console` | H2 DB console (local only) |

Import Grafana dashboard ID **19004** for a full Spring Boot 3 dashboard.

## Deployment

See [SETUP.md](SETUP.md) for Railway (backend) and Vercel (frontend) deployment instructions.
