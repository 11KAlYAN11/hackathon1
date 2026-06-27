# How to run & navigate — ZipRun AI Reassignment Engine

---

## Starting the app

### Backend (IntelliJ)
1. Open `hackathon/hackathon` as a Maven project in IntelliJ
2. Run → Edit Configurations → HackathonApplication
3. Add environment variable: `LLM_API_KEY=<your_groq_key>`
4. Run `HackathonApplication` — backend starts on **http://localhost:8080**

### Frontend (terminal)
```bash
cd hackathon/frontend
npm run dev
```
Opens on **http://localhost:5173**

---

## Backend — all URLs

| Method | URL | What it does |
|--------|-----|--------------|
| GET | `http://localhost:8080/agents` | List all 5 agents |
| PATCH | `http://localhost:8080/agents/AGT-001/status?status=OFFLINE` | Take agent offline → triggers agentic replan |
| PATCH | `http://localhost:8080/agents/AGT-001/status?status=AVAILABLE` | Bring agent back online |
| GET | `http://localhost:8080/orders` | All 8 orders |
| GET | `http://localhost:8080/orders?status=REASSIGNMENT_PENDING` | Orders needing reassignment |
| POST | `http://localhost:8080/orders/ORD-001/suggest` | Manually trigger AI suggestion for one order |
| GET | `http://localhost:8080/suggestions` | All suggestions |
| PATCH | `http://localhost:8080/suggestions/1` | Accept/reject suggestion (body below) |
| GET | `http://localhost:8080/actuator/health` | Health check → should return `{"status":"UP"}` |
| GET | `http://localhost:8080/h2-console` | H2 database browser |

**PATCH /suggestions/{id} body:**
```json
{ "status": "ACCEPTED" }
```
or
```json
{ "status": "REJECTED" }
```

**H2 Console login:**
- JDBC URL: `jdbc:h2:mem:hackathon`
- Username: `sa`
- Password: *(leave blank)*

---

## Frontend — http://localhost:5173

### Tab 1: Pending Reassignments
- Shows all suggestions with status = PENDING
- Each card shows: order ID, description, recommended agent, confidence %, AI reasoning
- **⚡ Agent Offline — Re-plan** badge = triggered by agentic loop (AGENT_OFFLINE)
- **🔍 On-demand** badge = manually triggered via POST /suggest
- Accept / Reject buttons call PATCH /suggestions/{id}
- Auto-refreshes every 4 seconds — no manual reload needed

### Tab 2: All Orders
- Lists all 8 orders with current status
- Status colours: blue=ASSIGNED, yellow=REASSIGNMENT_PENDING, green=REASSIGNED, grey=DELIVERED

### Agent Roster (left sidebar)
- Shows all 5 agents with live status (green=AVAILABLE, orange=BUSY, red=OFFLINE)
- **Go Offline** button → triggers the agentic loop for that agent's orders
- **Set Available** button → brings agent back

---

## Demo flow (for the 5-min video)

1. Open **http://localhost:5173** — show agents sidebar, all BUSY/AVAILABLE
2. Show **All Orders tab** — 8 orders all ASSIGNED
3. Click **Go Offline** on Priya Sharma (AGT-001) in the sidebar
4. Switch to **Pending Reassignments tab** — 3 cards appear automatically (ORD-001, ORD-002, ORD-008)
5. Each card shows the **⚡ Agent Offline — Re-plan** badge
6. Show the AI reasoning text on each card
7. Click **Accept** on one — order moves to REASSIGNED, agent order count updates
8. Switch to **All Orders tab** — show that order is now REASSIGNED
9. Check `/actuator/health` in browser to show system health

---

## If something looks wrong

**"Whitelabel Error Page" on backend** — you're hitting a URL that doesn't exist. All endpoints start at `/agents`, `/orders`, `/suggestions` (no `/api` prefix).

**UI shows plain text / no styles** — restart the Vite dev server (`npm run dev` in `hackathon/frontend`). Make sure old `main.ts` / `style.css` files are deleted from `src/`.

**Suggestions not appearing after going offline** — wait 2-3 seconds and the UI auto-polls. Or manually refresh the page.

**LLM fallback (confidence 0.6, generic reasoning)** — the `LLM_API_KEY` env var isn't set in IntelliJ run config. Add it and restart.
