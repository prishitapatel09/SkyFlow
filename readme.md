# SkyFlow — Flight Booking Platform

A scalable flight booking platform built with a **Java Spring Boot microservices** backend and a
**React + TypeScript** frontend.

### 🚀 Live Demo: **https://sky-flow-using-go-nsn8.vercel.app/**

> ⚠️ **Note to Recruiters & Reviewers**
>
> To keep this project free to host, the backend is deployed with replicas set to `0`
> (scale-to-zero).
> The **first API request may take 2–3 minutes** to wake the container.
> Once running, subsequent requests are fast and responsive.
> Thank you for your patience!

---

## 📖 Overview

SkyFlow is a full-stack flight booking platform demonstrating:

- Microservices architecture in Java 21 and Spring Boot 3.5
- Spring Cloud Gateway edge with JWT verification and Redis rate limiting
- PostgreSQL relational modelling, a schema per service, Flyway migrations
- Redis caching on flight search and seat inventory
- RabbitMQ event-driven email notifications
- Raft-inspired leader election over gRPC for master–worker task distribution
- Stripe payment integration
- LLM-assisted flight search and customer support with Claude
- Kubernetes manifests, Terraform, Prometheus and Grafana
- Dockerised local development

The backend handles booking workflows, seat inventory, payments and async email.
The frontend provides flight search, checkout, and an assistant that answers questions about
your bookings.

---

## 🐳 Run Locally (Fastest Way)

The easiest way to run the full project without installing Java, Node.js, PostgreSQL, Redis or
RabbitMQ manually is using **Docker**.

### ✅ Prerequisites

- Install **Docker Desktop**
- Ensure Docker is running

### 1️⃣ Clone the Repository

```bash
git clone https://github.com/prishitapatel09/SkyFlow.git
cd SkyFlow
```

### 2️⃣ Start Everything

```bash
./start-dev.sh
```

That's it 🚀

The entire stack will start:

- Seven Spring Boot services
- PostgreSQL
- Redis
- RabbitMQ
- React frontend
- Prometheus + Grafana

Flyway seeds ten cities, ten airports, four aircraft and a fortnight of departures, so search
returns results immediately.

| | |
|---|---|
| Frontend | http://localhost:3001 |
| API gateway | http://localhost:8080 |
| Cluster state | http://localhost:8082/api/v1/cluster/status |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| RabbitMQ | http://localhost:15672 (guest / guest) |
| Grafana | http://localhost:3000 (admin / admin) |

### 3️⃣ Set Environment Variables (Optional)

`./start-dev.sh` writes a `.env` on first run. Everything works without any API keys — two
features need them:

```bash
# Natural language search and the support assistant
ANTHROPIC_API_KEY=sk-ant-...

# Creating a booking (payment intent)
STRIPE_SECRET_KEY=sk_test_...
```

Without them those endpoints return a clear error and the rest of the platform is unaffected.

---

## 🧩 Services

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Routing, CORS, rate limiting, JWT verification |
| `flight-service` | 8081 | Flight search, seat inventory, reference data |
| `booking-service` | 8082 | Bookings, seat holds, coordination cluster (×3 replicas) |
| `payment-service` | 8083 | Stripe payment intents, refunds, webhooks |
| `notification-service` | 8084 | Consumes events, renders and sends email |
| `ai-service` | 8085 | Claude-backed search and support assistant |
| `user-service` | 8086 | Registration, login, JWT issuance, profiles |

Plus two shared libraries: `common` (response envelope, errors, events, cache and JWT config) and
`cluster-core` (the election and task-distribution implementation).

---

## 🏗️ System Architecture

### 🔄 Data Flow

```
Frontend (React + TypeScript on Vite)
        ↓
API Gateway (Spring Cloud Gateway) — JWT verified once, identity injected downstream
        ↓
Microservices (Spring Boot):
   – user           accounts, token issuance
   – flight         search, seat inventory
   – booking  ×3    bookings, seat holds, elected master
   – payment        Stripe
   – notification   email delivery
   – ai             Claude
        ↓
PostgreSQL (schema per service)  +  Redis (search and inventory cache)
        ↓
RabbitMQ (skyflow.events)
        ↓
notification-service → email

booking-service replicas coordinate privately over gRPC:
   RequestVote · Heartbeat · AssignTask · ReportTaskResult
```

### 🧠 Architecture Highlights

- Seven independently deployable services behind a single gateway
- JWT verified once at the edge; client-supplied identity headers are stripped
- Redis caching on the two hot read paths, with a TTL chosen per cache
- Seat reservation as one conditional `UPDATE`, so a flight cannot oversell
- Raft-inspired election, so periodic work runs exactly once across replicas
- Automatic failover: work stranded on a dead worker is reassigned
- Email decoupled behind RabbitMQ, with retries and a dead-letter queue
- Read-only LLM assistant that cannot book, pay, cancel or refund
- 58 tests covering the election, failover, booking correctness and auth

---

## 🎯 Why This Project Stands Out

This isn't just a CRUD app.

It demonstrates:

- Real microservices separation, not a modular monolith
- Distributed coordination implemented from the protocol up, rather than pulled from a library
- Correctness under concurrency — no oversold seats, idempotent event consumers
- Observability wired in: Micrometer → Prometheus → Grafana, with alert rules
- Infrastructure as code: Kubernetes manifests and Terraform
- LLM integration with a deliberate safety boundary

Well suited to backend, distributed systems and platform engineering roles.

---

## 📚 Deeper Detail

Design decisions and trade-offs, how the election and task distribution actually work, the
caching strategy, the full API reference and what the tests cover:

**[docs/architecture.md](docs/architecture.md)**

---

## 📂 Repository Layout

```
services/     Seven Spring Boot services + two shared libraries
frontend/     React 19 + TypeScript + Vite
infra/        docker/ · k8s/ · monitoring/ · terraform/
docs/         Architecture and design notes
```
