# Setup Guide

Everything you need to go from zero to a running local environment and a live deployed URL.

---

## Local Development Setup

### Step 1 — Clone and verify tools

```bash
git clone https://github.com/11KAlYAN11/hackathon1.git
cd hackathon1

java -version      # must be 17+
mvn -version       # must be 3.9+
node -v            # must be 20+
docker --version   # any version
```

### Step 2 — Environment variables

```bash
# Copy the template
cp .env.example .env

# Edit .env and fill in:
# - LLM_API_KEY  (Groq free key from console.groq.com)
# - DB_PASSWORD  (can leave as 'postgres' for local)
```

**Never commit `.env`** — it is in `.gitignore`.

### Step 3 — Start infrastructure (Postgres + Prometheus + Grafana)

```bash
docker compose up -d

# Verify all three are running
docker compose ps
```

### Step 4 — Start backend

```bash
cd hackathon

# Load env vars and run
export LLM_API_KEY=your_groq_key_here        # Mac/Linux
set LLM_API_KEY=your_groq_key_here           # Windows CMD
$env:LLM_API_KEY="your_groq_key_here"        # Windows PowerShell

./mvnw spring-boot:run                        # Mac/Linux
.\mvnw.cmd spring-boot:run                    # Windows
```

Confirm it started: `http://localhost:8080/actuator/health` → `{"status":"UP"}`

### Step 5 — Start frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`

---

## H2 Database Console (local only)

1. Open `http://localhost:8080/h2-console`
2. JDBC URL: `jdbc:h2:mem:hackathon`
3. Username: `sa` | Password: *(empty)*

---

## Observability — Prometheus + Grafana

Runs via Docker Compose automatically after `docker compose up -d`.

### Prometheus
- URL: `http://localhost:9090`
- Scrapes Spring Boot metrics every 5 seconds from `/actuator/prometheus`
- Try query: `http_server_requests_seconds_count`

### Grafana
- URL: `http://localhost:3001`
- Login: `admin` / `admin`
- Prometheus datasource is **auto-provisioned** on first launch

### Import the Spring Boot dashboard
1. Grafana → **Dashboards → Import**
2. Enter dashboard ID: **19004**
3. Select Prometheus datasource → **Import**
4. Done — live metrics for HTTP requests, JVM memory, DB pool, response times

---

## Switching the LLM Provider

Edit `application.properties` (local) or Railway env vars (prod):

```properties
# Groq (recommended — fast, free)
llm.provider=groq
llm.model=llama-3.3-70b-versatile
llm.endpoint=https://api.groq.com/openai/v1/chat/completions
llm.api.key=${LLM_API_KEY}

# Gemini (also free)
llm.provider=gemini
llm.model=gemini-1.5-flash
llm.endpoint=https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent

# Ollama (local, no key needed — pull model first)
# ollama pull llama3.1
llm.provider=ollama
llm.model=llama3.1
llm.endpoint=http://localhost:11434/v1/chat/completions
llm.api.key=ollama
```

---

## Railway Deployment (Backend)

Railway project: **practical-tenderness**
Project URL: `https://railway.com/project/84273ae1-7539-4f93-a0e1-f0bcc9bf6c23`

### One-time setup (already done)
The Railway project, PostgreSQL plugin, and all environment variables are already configured via CLI.

### Deploy

```bash
# Option 1 — auto deploy on git push (connect GitHub in Railway dashboard)
git push origin master

# Option 2 — deploy directly from CLI
cd hackathon
railway service hackathon-backend
railway up
```

### Environment variables on Railway

| Variable | Value |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://postgres.railway.internal:5432/railway` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | *(set in Railway dashboard)* |
| `LLM_API_KEY` | *(your Groq key — set in Railway dashboard)* |
| `LLM_PROVIDER` | `groq` |
| `LLM_MODEL` | `llama-3.3-70b-versatile` |
| `AI_BASE_URL` | `https://api.groq.com/openai/v1/chat/completions` |
| `ALLOWED_ORIGINS` | `http://localhost:5173,https://your-app.vercel.app` |

### Update a variable via CLI

```bash
cd hackathon
railway service hackathon-backend
railway variables set LLM_API_KEY=your_new_key_here
```

### View live logs

```bash
railway logs
```

### Health check

After deploy: `https://your-backend.up.railway.app/actuator/health`

---

## Vercel Deployment (Frontend)

### One-time setup
1. Go to [vercel.com](https://vercel.com) → **Add New Project**
2. Import GitHub repo `11KAlYAN11/hackathon1`
3. Set **Root Directory** to `frontend`
4. Framework preset: **Vite** (auto-detected)
5. Add environment variable:
   ```
   VITE_API_BASE_URL = https://your-backend.up.railway.app
   ```
6. Click **Deploy**

### Deploy on push
Every `git push origin master` auto-redeploys Vercel. No action needed.

### Update backend URL after Railway deploy

```bash
# In Vercel dashboard → Project → Settings → Environment Variables
VITE_API_BASE_URL=https://your-actual-railway-url.up.railway.app
```

Or via Vercel CLI:
```bash
npm i -g vercel
vercel env add VITE_API_BASE_URL
```

---

## Full Stack Deploy Checklist (Session Day)

```
[ ] Groq API key obtained and tested (console.groq.com)
[ ] railway variables set LLM_API_KEY=your_key  (1 command)
[ ] git push origin master  →  Railway redeploys backend (~2 min)
[ ] Set VITE_API_BASE_URL in Vercel to Railway URL
[ ] git push origin master  →  Vercel redeploys frontend (~30 sec)
[ ] Test: curl https://backend.railway.app/actuator/health
[ ] Test: open https://your-app.vercel.app
[ ] Update ALLOWED_ORIGINS on Railway to include Vercel URL
```

---

## Switching from H2 to Local Postgres (optional)

If you want to use Postgres locally instead of H2:

```bash
# Start Postgres via Docker Compose (already in docker-compose.yml)
docker compose up -d postgres

# Add to application.properties or set env vars:
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://localhost:5432/hackathon
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

---

## Common Issues

**Port 8080 already in use**
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <pid> /F

# Mac/Linux
lsof -ti:8080 | xargs kill
```

**LLM returns non-JSON response**
The `LLMGateway` has a fallback — it assigns the first available online agent and logs a warning. Check logs for `LLM returned unparseable JSON`.

**Hallucinated agentId from LLM**
Handled automatically — `RoutingService` validates the returned `agentId` against the database and falls back if it doesn't exist.

**Railway deploy fails**
```bash
railway logs   # check build and runtime logs
```
Most common cause: `SPRING_PROFILES_ACTIVE` not set to `prod`, so it tries to use H2 instead of Postgres.
