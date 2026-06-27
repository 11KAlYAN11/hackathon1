# ZipRun AI Reassignment Engine — Explained Simply

---

## The Problem (What ZipRun faces every day)

ZipRun is a last-mile delivery company. At any moment they have:
- **Delivery agents** out on the road making deliveries
- **Orders** assigned to those agents

Sometimes an agent suddenly goes **OFFLINE** — their phone dies, they meet with an accident, they log out mid-shift. The orders they were carrying are now **stranded** — nobody is delivering them.

In today's world, an operations manager gets a phone alert, manually figures out which orders that agent had, manually checks who is available, manually calls or reassigns each order one by one. If there are 10 stranded orders, this takes 15–20 minutes and causes massive delivery delays.

**That's the exact problem we're solving.**

---

## What We Built

An **AI-powered automatic reassignment engine** that detects when an agent goes offline and instantly suggests which other agent should pick up each stranded order — in under 3 seconds, no human intervention needed to generate the suggestions.

The ops team still makes the final call (Accept / Reject), but instead of spending 20 minutes figuring out options, they just review and click.

---

## How It Works — Step by Step (Layman's Terms)

### Step 1: Agent goes OFFLINE
Someone clicks "Go Offline" in the dashboard (or it can be triggered by a system event). The moment that happens, our backend does two things simultaneously:
- Responds immediately ("OK, status updated") so the UI doesn't hang
- Quietly fires an internal alarm: *"Hey, AGT-001 just went offline!"*

### Step 2: The Agentic Loop kicks in (the AI part)
A background worker wakes up the moment it hears that alarm. It:
1. Looks up all orders that were assigned to the offline agent
2. For each stranded order, asks the AI: *"Here's the order details, here are all available agents and how busy they are — who should take this order and why?"*
3. The AI (Groq / LLaMA 70B) replies with: a recommended agent, a confidence score (e.g. 87%), and a written reason

### Step 3: Suggestions appear on the dashboard
Within 2–3 seconds, the ops team sees cards appear on their screen — one per stranded order. Each card shows:
- Which order needs reassignment
- Which agent the AI recommends
- How confident the AI is (shown as a coloured bar — green=high, orange=medium, red=low)
- The AI's reasoning in plain English (e.g. *"Rahul Verma is currently AVAILABLE with 0 active orders, making him the best candidate to handle this urgent delivery"*)

### Step 4: Ops approves or rejects
The ops person reads the reasoning, clicks **Accept** or **Reject**. If accepted:
- The order is officially reassigned to the new agent
- Agent counters update automatically (old agent loses the order, new agent gains it)
- Order status changes from REASSIGNMENT_PENDING → REASSIGNED

Done. What used to take 20 minutes now takes under 30 seconds.

---

## The Agent Online / Offline Concept

Think of agents like Uber/Swiggy drivers:

| Status | What it means |
|--------|--------------|
| **AVAILABLE** | Agent is online, free, can take new orders |
| **BUSY** | Agent is online, currently delivering orders |
| **OFFLINE** | Agent has gone dark — can't reach them, no deliveries happening |

When an agent goes **OFFLINE**:
- Their existing orders become "orphaned" (no one is delivering them)
- Our system detects this automatically
- The AI immediately figures out who else can take those orders
- Ops sees suggestions within seconds and approves with one click

When an agent comes back **ONLINE** (set to AVAILABLE):
- They're ready to receive new orders again
- The system doesn't auto-reassign back (the orders they lost are already handled)

---

## Why AI? Why Not Just Assign to the Least-Busy Agent?

We have a rule-based fallback that does exactly that — picks the agent with fewest active orders. It works. But it's dumb.

The AI considers things like:
- The nature of the order (heavy item? urgent?)
- The agent's current workload and capacity
- Context about the offline situation (emergency vs scheduled offline)

Also, the AI **explains its decision**. The ops person can read *why* Rahul was chosen over Kiran. That trust and transparency is what makes humans comfortable clicking Accept instead of second-guessing every suggestion.

If the AI fails (network error, bad response, hallucinated agent ID that doesn't exist), the system automatically falls back to the rule-based approach. The ops team always gets a suggestion — never a blank screen.

---

## What Happens If the AI Goes Wrong?

We built three layers of protection:

1. **Hallucination guard** — If the AI recommends an agent ID that doesn't exist in our database, we ignore the AI response and fall back to rule-based. We never assign an order to a phantom agent.

2. **JSON parse guard** — If the AI returns garbled text instead of proper JSON, we catch the error and fall back to rule-based.

3. **Exception guard** — If the AI API is down entirely, we fall back to rule-based. The system never crashes.

The ops team always gets a suggestion. It might be AI-powered or rule-based — either way, it's there.

---

## The Numbers (Seeded Demo Data)

| Agent | Status | Active Orders |
|-------|--------|---------------|
| AGT-001 Priya Sharma | BUSY | 2 (ORD-001, ORD-002) |
| AGT-002 Rahul Verma | AVAILABLE | 0 |
| AGT-003 Ananya Iyer | BUSY | 1 (ORD-003) |
| AGT-004 Kiran Nair | AVAILABLE | 0 |
| AGT-005 Deepak Mehta | BUSY | 3 (ORD-006, ORD-007, ORD-008*) |

*ORD-008 is also assigned to AGT-001 in the seeded data, so taking Priya offline strands ORD-001, ORD-002, and ORD-008 — 3 simultaneous AI suggestions generated.

---

## Tech Behind It (One Line Each)

- **Spring Boot** — the backend server that handles all requests
- **H2 Database** — lightweight in-memory database (data resets on restart, perfect for demo)
- **Groq + LLaMA 70B** — the AI model that generates recommendations
- **ApplicationEventPublisher** — Spring's internal messaging system that triggers the agentic loop without blocking
- **@Async** — makes the AI calls happen in a background thread so the API responds instantly
- **React + Vite** — the frontend dashboard that ops uses
- **Prometheus + Grafana** — monitoring (response times, error rates, agent status trends)

---

## In One Sentence

When a delivery agent goes offline, instead of an ops manager spending 20 minutes manually figuring out what to do, our AI engine detects it instantly, generates smart reassignment suggestions with reasoning in under 3 seconds, and the ops team approves with one click.
