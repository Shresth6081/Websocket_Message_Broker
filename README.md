# 🚀 Distributed WebSocket Message Broker
### High-Performance Real-Time Chat System | Spring Boot Microservices

[![Live Demo](https://img.shields.io/badge/Live%20Demo-Render-46E3B7.svg?style=for-the-badge&logo=render)](https://chat-api-gateway-40se.onrender.com)
[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-7.5-black.svg?style=for-the-badge&logo=apachekafka)](https://kafka.apache.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg?style=for-the-badge&logo=docker)](https://docs.docker.com/compose/)

> 🌐 **Live Demo**: **[https://chat-api-gateway-40se.onrender.com](https://chat-api-gateway-40se.onrender.com)**  
> *(Hosted live on Render Free Tier with Aiven Cloud Kafka & Managed PostgreSQL)*

A distributed, real-time messaging platform built using Spring Boot Microservices, Apache Kafka, WebSockets (STOMP), PostgreSQL, and a modern Tailwind CSS v4 dark-mode UI. Monitored in real-time with Prometheus and Grafana dashboards.

> [!NOTE]
> This system is designed for horizontal scaling. Chat services handle incoming WebSocket client connections and communicate via Apache Kafka to coordinate message delivery across different nodes.

---

## 🏗️ Architecture

```
 Browser (SockJS+STOMP)
        │  WebSocket / REST
        ▼
┌─────────────────────────┐
│      API Gateway        │  :8085 (Exposed Gateway Port)
│  • Bucket4j rate limit  │  (20 req/10s per IP)
│  • JWT auth filter      │  (Bypassed for WS handshakes)
│  • Request routing      │
└────────┬────────────────┘
         │
   ┌─────┴──────┐
   ▼            ▼
┌──────┐  ┌─────────────────────────────────┐
│ User │  │        Chat Service             │  :8081
│ Svc  │  │  • STOMP WebSocket broker       │
│:8082 │  │  • Kafka producer / consumer    │
│      │  │  • PostgreSQL persistence       │
│ JWT  │  │  • Public & Private rooms       │
│ auth │  │  • 1-on-1 Direct Messages (DMs) │
│      │  │  • Prometheus metrics           │
└──────┘  └──────────────┬──────────────────┘
                         │  Kafka
                ┌────────▼──────────┐
                │  Apache Kafka     │  :9092 (external)
                │  + Zookeeper      │  :29092 (internal)
                └───────────────────┘
```

---

## 🌟 Key Features

* **Distributed Messaging**: Chat Service scales horizontally by broadcasting STOMP messages over Kafka topics (`chat-messages`).
* **API Gateway Routing**: Spring Cloud Gateway acts as the single entry point. Bypasses JWT validation for WebSocket handshakes (which are validated internally at the service level using query tokens) and root frontend requests.
* **Token-Bucket Rate Limiter**: Implements IP-based rate limiting via Bucket4j (20 requests / 10 seconds).
* **Private Channels & Invites**: Create lockable private channels. Members of the channel can invite other users by username.
* **1-on-1 Direct Messages (DMs)**: Initiate private chats with other users. Names are resolved dynamically and rendered under a separate DM sidebar section.
* **Slack-style Message Grouping**: Frontend groups consecutive messages from the same user within 5 minutes, significantly reducing visual clutter.
* **Actuator Monitoring**: Exposes Prometheus metrics (`chat_active_sessions`, `chat_messages_total`) visualized on a pre-provisioned Grafana Dashboard.

---

## 🚀 Quick Start (Docker — Recommended)

> [!TIP]
> Compiling the JARs on the host machine is much faster because it leverages Maven's local dependency cache (usually taking ~1 minute vs. 10+ minutes inside a Docker container build).

### 1. Compile the microservices locally
We compile JARs on the host. A portable Maven instance is automatically downloaded if not present.
```powershell
# In PowerShell or Command Prompt
.\build-jars.bat
```

### 2. Launch the Docker containers
```powershell
docker compose up -d --build
```
Verify that all 8 containers are running and healthy:
```powershell
docker compose ps
```

### 3. Open the chat UI
Open your browser and navigate to:
👉 **[http://localhost:8085](http://localhost:8085)**

---

## 🧪 Testing & Verification Guide

### Step 1 — Register Users
You can register users via the UI, or run `curl` commands directly against the Gateway:
```bash
# Register Alice
curl -X POST http://localhost:8085/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","displayName":"Alice","email":"alice@test.com","password":"secret123"}'

# Register Bob
curl -X POST http://localhost:8085/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","displayName":"Bob","email":"bob@test.com","password":"secret123"}'
```

### Step 2 — Login to fetch JWT Token
```bash
curl -X POST http://localhost:8085/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}'
```

### Step 3 — Test the Rate Limiter (Bucket4j)
Send 25 rapid HTTP requests to the rooms endpoint. The gateway will block you with a `429 Too Many Requests` status after the 20th request:

> [!IMPORTANT]
> The gateway has a rate limit of 20 requests per 10 seconds. You can verify this by running the following loop:

```powershell
# PowerShell Loop
for ($i=1; $i -le 25; $i++) {
  Invoke-RestMethod -Uri "http://localhost:8085/api/chat/rooms" -Headers @{ Authorization="Bearer <JWT_TOKEN>" }
}
```

---

## 📊 Observability (Prometheus & Grafana)

| Service | URL | Credentials |
|---------|-----|-------------|
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | — |
| **Grafana** | [http://localhost:3000](http://localhost:3000) | `admin` / `admin` |

### Grafana Dashboard Features:
* **JVM Heap Usage**: Memory tracking across the Gateway, User, and Chat services.
* **Active Connections**: Active WebSocket/STOMP sessions (`chat_active_sessions`).
* **Kafka Consumer Lag**: Max record lag for consumers on the `chat-messages` topic.
* **Blocked Rate Limits**: Count of requests blocked by Bucket4j gateway filter.

---

## 🔗 API Reference

### User Service (port `8082`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/users/register` | None | Register a new user |
| POST | `/api/users/login` | None | Verify password and return JWT |
| GET | `/api/users/exists/{username}` | None | Internal check if user exists |

### Chat Service (port `8081`)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/chat/rooms` | JWT | Get rooms current user belongs to |
| POST | `/api/chat/rooms` | JWT | Create a new PUBLIC or PRIVATE room |
| POST | `/api/chat/rooms/{roomId}/invite` | JWT | Invite a user to a private room |
| POST | `/api/chat/rooms/dm` | JWT | Start or fetch 1-on-1 DM room |
| GET | `/api/chat/rooms/{roomId}/messages` | JWT | Fetch paged history (checks membership) |

---

## 🌿 Repository Branches

| Branch | Description | Target Environment |
|---|---|---|
| **`main`** | Local development codebase with Docker Compose | Local (`localhost:8085`) |
| **`deploy-render`** | Cloud-native production deployment with Render Blueprint | Render Cloud (`*.onrender.com`) |

---

## 🔧 Technologies Used

* **Core Java Framework**: Spring Boot 3.2.5
* **Security & Auth**: Spring Security + JJWT 0.12.5 (HS256 encryption)
* **API Gateway**: Spring Cloud Gateway 2023.0.1 (WebClient-backed routing)
* **Real-time Engine**: Spring WebSocket + STOMP (Native WebSockets fallback to SockJS)
* **Distributed Messaging**: Apache Kafka + ZooKeeper (Confluent 7.5.0) / Aiven Cloud Kafka
* **Database**: PostgreSQL 16 + Spring Data JPA + Hibernate
* **Rate Limiting**: Bucket4j 8.10.1 (Token Bucket algorithm)
* **Metrics & Monitoring**: Micrometer, Prometheus, Grafana, Spring Actuator
* **UI**: Vanilla HTML5 / ES6 Javascript + Tailwind CSS v4 (Modest Slate/Zinc Theme)
* **Orchestration & Cloud**: Docker, Docker Compose, Render Blueprint
* **Hosting**: Render Free Tier

---
For more information, visit the [GitHub Repository](https://github.com/Shresth6081/Websocket_Message_Broker).

