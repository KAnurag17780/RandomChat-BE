# Random Chat Platform

A pseudonymous, account-based 1-to-1 random text chat backend in the spirit of Omegle —
built with **Java 21**, **Spring Boot 3.3**, **PostgreSQL**, **Raw WebSockets**, and **Flyway**.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Docker & Containerization](#docker--containerization)
4. [Configuration Reference](#configuration-reference)
5. [Running Tests](#running-tests)
6. [REST API Reference](#rest-api-reference)
7. [WebSocket Protocol Reference](#websocket-protocol-reference)
8. [Authentication Flow for Browser Integration](#authentication-flow-for-browser-integration)
9. [Project Structure](#project-structure)
10. [Known Limitations](#known-limitations)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java JDK | 21+ |
| Apache Maven | 3.9+ |
| Docker Desktop | Latest (for PostgreSQL + integration tests) |

> **No Docker?** You can still run unit tests and compile the project. Integration tests that
> require PostgreSQL will be skipped unless Docker is running.

---

## Quick Start

### 1. Clone and configure environment

```bash
git clone <repo-url>
cd random-chat-platform
cp .env.example .env
# Edit .env to set passwords / secrets if desired
```

### 2. Start PostgreSQL

```bash
docker compose up -d postgres
```

Wait for the health check to pass (takes ~10 seconds):

```bash
docker compose ps   # Status should show "healthy"
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The application starts on port **8080** by default.

- **Swagger UI:** http://localhost:8080/swagger-ui
- **API Docs (JSON):** http://localhost:8080/api-docs
- **Health Check:** http://localhost:8080/actuator/health

Flyway migrations run automatically on startup. The database schema is created from scratch if it does not exist.

---

## Docker & Containerization

The project includes a production-ready, multi-stage `Dockerfile` and `compose.yaml` configuration for local development and container deployments.

### 1. Multi-Stage Docker Build
The [`Dockerfile`](file:///c:/ChatApp/Dockerfile) uses a two-stage build for optimal image size, security, and build caching:
- **Build Stage:** `eclipse-temurin:21-jdk-alpine` caches Maven wrapper dependencies (`mvnw dependency:go-offline`) before compiling the application JAR.
- **Runtime Stage:** `eclipse-temurin:21-jre-alpine` provides a minimal lightweight JRE runtime.
- **Security:** Runs as an unprivileged, non-root user (`appuser:appgroup`).
- **Healthcheck:** Automatically polls the Spring Boot Actuator health endpoint (`/actuator/health`) every 30 seconds.

### 2. Building the Backend Docker Image

```bash
docker build -t random-chat-backend:latest .
```

### 3. Managing PostgreSQL with Docker Compose

[`compose.yaml`](file:///c:/ChatApp/compose.yaml) defines a dedicated PostgreSQL 16 container with health checks and volume persistence:

```bash
# Start PostgreSQL in the background
docker compose up -d postgres

# Check container status and healthcheck
docker compose ps

# View database logs in real time
docker compose logs -f postgres

# Stop PostgreSQL (preserves database data)
docker compose down

# Stop and completely reset the database data volume
docker compose down -v
```

### 4. Running the Backend Container

To run the containerized application alongside PostgreSQL:

```bash
docker run -d \
  --name random-chat-app \
  -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e POSTGRES_DB=randomchat \
  -e POSTGRES_USER=randomchat \
  -e POSTGRES_PASSWORD=devpassword \
  random-chat-backend:latest
```

*(On Linux without Docker Desktop, replace `DB_HOST=host.docker.internal` with your host IP or connect them via a shared Docker bridge network).*

### 5. Verifying Container Health

```bash
# View running containers (shows "(healthy)" once Actuator probe succeeds)
docker ps

# Inspect detailed Docker health check output
docker inspect --format='{{json .State.Health}}' random-chat-app
```

---

## Configuration Reference

All configuration is driven by environment variables. Copy `.env.example` to `.env` and adjust:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `randomchat` | Database name |
| `POSTGRES_USER` | `randomchat` | Database user |
| `POSTGRES_PASSWORD` | `devpassword` | Database password |
| `DB_HOST` | `localhost` | Database host |
| `DB_PORT` | `5432` | Database port |
| `DB_POOL_MAX` | `10` | HikariCP max pool size |
| `SERVER_PORT` | `8080` | HTTP / WebSocket port |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` in production (HTTPS only) |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | CORS allowed origins |
| `WS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | WebSocket allowed origins |
| `WS_MAX_MESSAGE_SIZE` | `8192` | Max WebSocket message size in bytes |
| `WS_HEARTBEAT_INTERVAL_SECONDS` | `30` | Heartbeat ping interval |
| `WS_HEARTBEAT_TIMEOUT_SECONDS` | `90` | Idle session timeout |

---

## Running Tests

### Unit tests (no Docker required)

```bash
mvn test
```

All unit tests run in-process. Currently covers:

- `MatchmakingEngineTest` — 9 tests: pairing, duplicate prevention, NEXT, LEAVE,
  disconnect, block-aware matching, self-match prevention, and idempotency.

### Integration tests (requires Docker)

```bash
mvn test -Pintegration-tests
```

Spins up a real PostgreSQL container via Testcontainers. Covers:

- `AuthIntegrationTest` — registration, login, uniqueness, email normalization, validation.

---

## REST API Reference

> Full interactive documentation is available at `/swagger-ui` when the application is running.

### Authentication

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | Public | Create a new account |
| POST | `/api/auth/login` | Public | Login and establish session |
| POST | `/api/auth/logout` | Required | Invalidate current session |
| GET | `/api/auth/me` | Required | Current user info |

**Register request:**
```json
{
  "email": "alice@example.com",
  "password": "SecurePass123!",
  "username": "alice"
}
```

**Login request:**
```json
{ "email": "alice@example.com", "password": "SecurePass123!" }
```

### Profile

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/profile/me` | Required | Get your own profile |
| PUT | `/api/profile/me` | Required | Update your profile |
| GET | `/api/profiles/{username}` | Required | Get a public profile by username |

### Conversations and Message History

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/conversations` | Required | List your past conversations |
| GET | `/api/conversations/{id}` | Required | Get one conversation (participant only) |
| GET | `/api/conversations/{id}/messages?cursor=&limit=` | Required | Paginated message history |

### Moderation

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users/{userId}/block` | Required | Block a user |
| DELETE | `/api/users/{userId}/block` | Required | Unblock a user |
| GET | `/api/blocks` | Required | List blocked users |
| POST | `/api/reports` | Required | Submit a report |

---

## WebSocket Protocol Reference

**Endpoint:** `ws://localhost:8080/ws/chat`

**Authentication:** The server reads the `RCPSESSION` cookie set by `/api/auth/login`.
The browser sends it automatically with the WebSocket upgrade request.

### Client to Server

| Action | Description |
|--------|-------------|
| `FIND_MATCH` | Start searching for a partner |
| `SEND` | Send a text message (with optional `messageId` for idempotency) |
| `NEXT` | Skip current partner, re-queue for a new match |
| `LEAVE` | Leave conversation, go idle |
| `TYPING` | Typing indicator (`isTyping: true/false`) |
| `PING` | Heartbeat keepalive |

### Server to Client

| Event | Description |
|-------|-------------|
| `SEARCHING` | Now searching for a match |
| `MATCHED` | Paired with a partner; includes `pairId`, `conversationId`, `partner` info |
| `MESSAGE` | Incoming message from partner |
| `MESSAGE_ACK` | Confirmation your message was persisted |
| `PARTNER_LEFT` | Partner disconnected (`NEXT`, `LEAVE`, or `DISCONNECT`) |
| `TYPING` | Partner typing status |
| `PONG` | Heartbeat response |
| `ERROR` | Error description |

**Example MATCHED event:**
```json
{
  "type": "MATCHED",
  "pairId": "uuid",
  "conversationId": "uuid",
  "partner": { "displayName": "Stranger", "avatarUrl": null }
}
```

**Example SEND message:**
```json
{ "action": "SEND", "messageId": "client-uuid", "text": "Hello!" }
```

---

## Authentication Flow for Browser Integration

1. `POST /api/auth/login` — server sets a `RCPSESSION` `HttpOnly` cookie.
2. `new WebSocket("ws://localhost:8080/ws/chat")` — browser sends the cookie automatically.
3. Send `{ "action": "FIND_MATCH" }` to start searching.
4. On `MATCHED`, begin chatting with `SEND` actions.
5. On `PARTNER_LEFT`, optionally send `FIND_MATCH` again.

> In production: use HTTPS/WSS and set `SESSION_COOKIE_SECURE=true`.

---

## Project Structure

```
src/
├── main/java/dev/randomchat/platform/
│   ├── auth/            # Registration, login, session management
│   ├── user/            # User entity, profile management
│   ├── chat/
│   │   ├── handler/     # ChatWebSocketHandler, HeartbeatScheduler, WebSocketSender
│   │   ├── matchmaking/ # MatchmakingEngine, ChatPair
│   │   ├── protocol/    # ClientAction, ServerEvent
│   │   └── session/     # ChatSession, SessionRegistry, UserState
│   ├── conversation/    # Persistent history (entity, repo, service, controller)
│   ├── moderation/      # Block/report (entity, repo, service, controller)
│   └── common/          # Security, CORS, WebSocket config, exception handling
└── main/resources/
    ├── application.yml
    └── db/migration/    # V1-V5 Flyway migrations
```

