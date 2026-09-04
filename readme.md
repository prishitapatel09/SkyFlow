# SkyFlow

Airline booking platform built as Java Spring Boot microservices with a React front end.

Flight search and inventory lookups are served from Redis. Periodic work is distributed
master–worker across booking-service replicas, which elect a leader using a Raft-inspired protocol
over gRPC heartbeats. Email is decoupled behind RabbitMQ. Natural-language flight search and a
support assistant are backed by Claude.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Cloud Gateway · PostgreSQL · Redis · RabbitMQ · gRPC
· React 19 · TypeScript · Vite · Kubernetes · Terraform · Prometheus/Grafana · Stripe · Anthropic
Claude

## Live demo

**https://sky-flow-using-go-nsn8.vercel.app/**

> **What you are looking at.** That deployment serves the React front end against a **Go**
> implementation of SkyFlow, built by [@SomyaPadhy4501](https://github.com/SomyaPadhy4501/SkyFlow-using-GO)
> — same product, same API shape, different backend language. It is there so you can click through
> the booking flow without running anything.
>
> **This repository is the Java / Spring Boot implementation** described below, and it is not the
> code behind that URL. To exercise this backend, run it locally with
> [`./start-dev.sh`](#running-it-locally) — it comes up seeded and needs no API keys.
>
> The hosted backend is scaled to zero to keep it free, so the first request takes 2–3 minutes to
> wake the container. Everything after that is fast.

---

## Contents

- [Live demo](#live-demo)
- [Architecture](#architecture)
- [Running it locally](#running-it-locally)
- [The parts worth reading](#the-parts-worth-reading)
  - [Redis caching on the search path](#redis-caching-on-the-search-path)
  - [Raft-inspired election and master–worker distribution](#raft-inspired-election-and-masterworker-distribution)
  - [RabbitMQ-decoupled email](#rabbitmq-decoupled-email)
  - [LLM-assisted search and support](#llm-assisted-search-and-support)
  - [Seats that cannot oversell](#seats-that-cannot-oversell)
- [API](#api)
- [Configuration](#configuration)
- [Tests](#tests)
- [Deploying](#deploying)
- [Design decisions and trade-offs](#design-decisions-and-trade-offs)

---

## Architecture

```
                         ┌───────────────┐
      browser ─────────► │   frontend    │  nginx serves the SPA and proxies /api
                         │  React + Vite │  (one origin, so no CORS on the hot path)
                         └───────┬───────┘
                                 │
                         ┌───────▼───────┐
                         │  api-gateway  │  Spring Cloud Gateway (reactive)
                         │               │  verifies the JWT once, injects X-User-*,
                         │               │  Redis-backed rate limiting
                         └───────┬───────┘
        ┌────────────┬───────────┼────────────┬──────────────┬─────────────┐
        ▼            ▼           ▼            ▼              ▼             ▼
 ┌────────────┐ ┌─────────┐ ┌──────────┐ ┌─────────┐ ┌──────────────┐ ┌──────────┐
 │   user     │ │ flight  │ │ booking  │ │ payment │ │ notification │ │    ai    │
 │  service   │ │ service │ │ service  │ │ service │ │   service    │ │ service  │
 │            │ │         │ │  ×3      │ │         │ │              │ │          │
 │ JWT issue  │ │ search  │ │ master–  │ │ Stripe  │ │ RabbitMQ →   │ │ Claude   │
 │ accounts   │ │ + seats │ │ worker   │ │ intents │ │ email        │ │ NL + chat│
 └─────┬──────┘ └────┬────┘ └────┬─────┘ └────┬────┘ └──────┬───────┘ └────┬─────┘
       │             │           │            │              │              │
       │        ┌────▼────┐      │       ┌────▼────┐         │              │
       └───────►│Postgres │◄─────┴──────►│Postgres │◄────────┘              │
                │(schema  │              │(schema  │                        │
                │ per svc)│              │ per svc)│                        │
                └─────────┘              └─────────┘                        │
                     ┌──────────┐   ┌──────────┐                            │
                     │  Redis   │◄──┤ RabbitMQ │                            │
                     │ cache +  │   │  events  │                            │
                     │ chat mem │   └──────────┘                            │
                     └────▲─────┘                                           │
                          └───────────────────────────────────────────────────

 booking-service replicas also speak a private gRPC coordination plane to each other:
   RequestVote · Heartbeat · AssignTask · ReportTaskResult
```

| Module | Port | gRPC | Responsibility |
|---|---|---|---|
| `api-gateway` | 8080 | — | Routing, CORS, rate limiting, JWT verification |
| `flight-service` | 8081 | — | Cities, airports, aircraft, flight search, seat inventory |
| `booking-service` | 8082 | 9090 | Bookings, seat holds, coordination cluster |
| `payment-service` | 8083 | — | Stripe payment intents, refunds, webhooks |
| `notification-service` | 8084 | — | Consumes events, renders and sends email |
| `ai-service` | 8085 | — | Claude-backed NL search and support assistant |
| `user-service` | 8086 | — | Registration, login, JWT issuance, profiles |
| `common` | — | — | Library: response envelope, errors, events, cache config, JWT |
| `cluster-core` | — | — | Library: Raft-inspired election, master–worker distribution |

---

## Running it locally

Requirements: Docker with Compose, and JDK 21 plus Node 20+ only if you want to build outside
Docker. Maven comes from the wrapper (`./mvnw`).

```bash
./start-dev.sh
```

That writes a `.env` on first run, builds every image, and starts the stack:

| | |
|---|---|
| Frontend | http://localhost:3001 |
| API gateway | http://localhost:8080 |
| Cluster state | http://localhost:8082/api/v1/cluster/status |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| RabbitMQ | http://localhost:15672 (guest / guest) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |

Flyway seeds ten cities, ten airports, four aircraft and a fortnight of departures on seven routes,
so search returns results immediately.

Everything works without any API keys. Two things need them:

- `ANTHROPIC_API_KEY` — natural-language search and the assistant. Without it those two endpoints
  return 502 and the rest of the platform is unaffected.
- `STRIPE_SECRET_KEY` — creating a booking calls Stripe for a payment intent, so booking fails
  without a test key. Search, accounts and everything else still work.

Other commands:

```bash
./start-dev.sh cluster   # three booking-service replicas, to watch the election
./start-dev.sh infra     # just Postgres, Redis, RabbitMQ - run services from your IDE
./start-dev.sh test      # every test suite
./start-dev.sh logs booking-service
./start-dev.sh reset     # stop and drop the volumes
```

---

## The parts worth reading

### Redis caching on the search path

Search and seat availability are the two hot reads, and they are the two the platform caches.
[`RedisCacheConfig`](services/common/src/main/java/com/skyflow/common/config/RedisCacheConfig.java)
defines a TTL per cache rather than one global value, because the right staleness differs by an
order of magnitude:

| Cache | TTL | Why |
|---|---|---|
| `flightSearch` | 1 hour | Rebuilt cheaply; the same route and date is asked for constantly |
| `flight` | 30 minutes | Individual flight with its joined airports |
| `seatInventory` | 30 seconds | Bookings move it continuously |
| `cities`, `airports`, `airplanes` | 6 hours | Reference data, changes by hand |

The cache key for a search is built explicitly in
[`FlightSearchRequest.cacheKey()`](services/flight-service/src/main/java/com/skyflow/flight/dto/FlightSearchRequest.java)
rather than derived from `toString()`, so adding a field later cannot silently invalidate every
existing key, and `" BOS "` and `"bos"` hit the same entry.

Writes evict precisely: reserving a seat drops that flight, its inventory entry, and the whole
search cache — the last one because there is no way to know which cached result sets contained that
flight. `FlightSearchCacheHitRatioLow` in [`monitoring/rules`](monitoring/rules/skyflow.yml) fires
if the hit ratio falls below 50%, since a collapsed ratio means Postgres is quietly serving the hot
path again.

### Raft-inspired election and master–worker distribution

`booking-service` runs three replicas, and some of its work must happen exactly once: expiring
unpaid seat holds, sending departure reminders, warming the search cache. Run those on every
replica and travellers get three reminder emails.

[`cluster-core`](services/cluster-core/src/main/java/com/skyflow/cluster) solves that with the
parts of Raft a task distributor actually needs, over two gRPC calls:

- **Monotonic terms and one vote per node per term.** A candidate increments its term, votes for
  itself, and needs a majority. Two leaders in one term is impossible.
- **Randomized election timeouts** (1.5–3s) so replicas do not all stand for election at once.
- **Step-down on a higher term.** A partitioned old leader learns it has been replaced the moment
  it hears a newer term, and its `AssignTask` calls are refused as stale.
- **Heartbeats as the failure detector.** The leader heartbeats every 500ms; three consecutive
  misses declare a worker dead.

It deliberately does *not* replicate a log. The pending task queue lives in the leader's memory, so
a leader change can re-run an in-flight task — which is why every
[`ClusterTaskHandler`](services/cluster-core/src/main/java/com/skyflow/cluster/ClusterTaskHandler.java)
is idempotent. That is the honest trade: real Raft, minus the machinery whose only purpose is
exactly-once, replaced by handlers that do not need it.

The same `Heartbeat` RPC does three jobs at once — the follower learns the leader is alive, the
leader learns the follower is alive, and the response carries the follower's current load so
dispatch can pick the least-loaded worker.

Work is recovered three ways, and together these are the automatic failover:

1. the worker reports a failure → requeue with the attempt count incremented;
2. the worker stops answering heartbeats → everything it held is requeued at once;
3. the deadline passes with no result → requeue.

Under Kubernetes the replicas are a **StatefulSet behind a headless service**, because a node's
identity *is* its advertised gRPC endpoint and the peer list must be stable across restarts. See
[`k8s/booking-service.yaml`](k8s/booking-service.yaml), and the `NetworkPolicy` in
[`k8s/networkpolicy.yaml`](k8s/networkpolicy.yaml) that restricts port 9090 to sibling pods.

Watch it happen:

```bash
./start-dev.sh cluster

curl -s localhost:8082/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'
docker compose stop booking-service          # kill the leader
curl -s localhost:8092/api/v1/cluster/status | jq '{nodeId,role,term,leaderId}'
```

The admin dashboard at `/admin` shows the same thing with a two-second refresh.

### RabbitMQ-decoupled email

Confirmations, cancellations, payment outcomes and departure reminders are published to a topic
exchange and delivered by `notification-service`. Checkout returns as soon as Stripe has the
intent; nothing waits on SMTP.

```
skyflow.events (topic)
├── notification.#  ─┐
├── booking.#       ─┼─► skyflow.notifications ──► notification-service
├── payment.#       ─┘                              │ 4 attempts, exponential backoff
│                                                   ▼
└── payment.#        ─► skyflow.booking.payments  skyflow.notifications.dlq
                        └─► booking-service            (via skyflow.events.dlx)
```

Delivery is at-least-once, so every consumer is idempotent —
[`BookingService.markPaid`](services/booking-service/src/main/java/com/skyflow/booking/service/BookingService.java)
returns early if the booking is already confirmed, and a payment that lands after the hold expired
is refunded rather than resurrected onto seats that may already have been resold.

[`EventPublisher`](services/common/src/main/java/com/skyflow/common/event/EventPublisher.java) logs
and swallows broker failures instead of throwing: a RabbitMQ outage should not fail a booking that
otherwise succeeded, which is the entire point of moving email off the request path.

### LLM-assisted search and support

Two separate uses of Claude (`claude-opus-5`), because they are different problems.

**Natural-language search** — `POST /api/v1/ai/search`

"cheap morning flight from Boston to SFO next Friday for two" becomes the same filters the search
form produces.
[`NaturalLanguageSearchService`](services/ai-service/src/main/java/com/skyflow/ai/service/NaturalLanguageSearchService.java)
uses **structured output**: Claude fills a `SearchCriteria` record whose JSON schema is derived from
the type, so there is no prompt-and-parse step to get wrong. The response includes the model's own
one-line restatement of what it searched for, which the UI shows so a wrong guess is visible and
correctable. Effort is `low` — this is a narrow extraction task.

**Support and booking assistant** — `POST /api/v1/ai/chat`

A hand-written tool loop (rather than the SDK's tool runner, because each call needs the caller's
identity threaded through it and a per-conversation iteration cap) over six tools in
[`AssistantTools`](services/ai-service/src/main/java/com/skyflow/ai/service/AssistantTools.java).
Effort is `medium`; conversation history lives in Redis with a two-hour TTL.

Every tool is a read. The assistant cannot book, pay, cancel, or refund — no tool of its does those
things. `start_booking` returns a checkout link the traveller opens themselves. Identity comes from
the authenticated request, never from a tool argument, so it cannot be talked into reading another
account. Cancellation stays a deliberate, confirmed action in "My bookings", where it releases seats
and triggers a refund.

### Seats that cannot oversell

Reservation is a single conditional `UPDATE`:

```sql
UPDATE flights SET available_seats = available_seats - :seats
WHERE id = :id AND available_seats >= :seats
```

Zero rows changed means not enough seats. No read-then-write, no lock held across a service call,
and the guarantee holds no matter how many `booking-service` replicas race for the last seat.

Seats are reserved *before* the booking row is written, and released again if anything downstream
fails — see the `try`/`catch` around payment-intent creation in `BookingService.create`. The other
order would hand out a confirmation for a flight that is already full.

Unpaid holds expire after 15 minutes, swept by the master–worker cluster above.

---

## API

Everything is behind the gateway at `/api/v1`, in a shared envelope:

```json
{ "success": true, "message": "Successfully fetched flights", "data": { }, "timestamp": "…" }
```

Public (no token): auth endpoints, `GET` on flights/cities/airports/airplanes, `POST /ai/search`,
the Stripe webhook.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/auth/register`, `/auth/login`, `/auth/refresh` | Returns `{token, refreshToken, user}` |
| `GET` `PUT` | `/auth/me`, `/auth/profile`, `/auth/password` | |
| `GET` | `/flights/search` | `from`/`to` by IATA code or city, date, price range, passengers, sort, paging |
| `GET` | `/flights/{id}`, `/flights/{id}/seats` | |
| `POST` | `/flights`, `/flights/{id}/seats/reserve`, `/seats/release` | Internal / admin |
| `GET` `POST` `PATCH` `DELETE` | `/cities`, `/airports`, `/airplanes` | |
| `POST` | `/bookings` | Creates the booking *and* its payment intent in one call |
| `GET` | `/bookings/my`, `/bookings/{id}`, `/bookings/reference/{ref}` | |
| `POST` | `/bookings/{id}/cancel` | Releases seats, refunds if paid |
| `GET` | `/bookings` | Admin only |
| `POST` | `/payments/intent`, `/{id}/confirm`, `/{id}/refund`, `/{id}/cancel`, `/{id}/sync` | |
| `GET` | `/payments/methods`, `/payments/{id}` | |
| `POST` | `/customers`, `GET /customers/{id}/payments` | Stripe customers |
| `POST` | `/webhooks/stripe` | Signature required; rejects if the signing secret is unset |
| `POST` | `/ai/search`, `/ai/chat`, `DELETE /ai/chat/{id}` | |
| `GET` | `/cluster/status` | Role, term, leader, worker load |
| `GET` | `/notifications` | Delivery history |

OpenAPI per service at `/swagger-ui.html`.

---

## Configuration

Every setting is an environment variable with a working default; see each service's
`application.yml`.

| Variable | Used by | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | all persistent services | `jdbc:postgresql://localhost:5432/skyflow` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | gateway, flight, booking, ai | `localhost:6379` |
| `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | flight, booking, payment, notification | `localhost`, guest |
| `JWT_SECRET` | user-service, gateway | dev placeholder — **must be ≥32 chars** |
| `ANTHROPIC_API_KEY` | ai-service | empty → endpoints report 502 |
| `CLAUDE_MODEL`, `CLAUDE_CHAT_EFFORT`, `CLAUDE_EXTRACTION_EFFORT` | ai-service | `claude-opus-5`, `medium`, `low` |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | payment-service | empty |
| `EMAIL_ENABLED`, `MAIL_HOST`, `EMAIL_USER`, `EMAIL_PASSWORD` | notification-service | `true` in k8s, `false` in compose |
| `CLUSTER_ENABLED`, `CLUSTER_ADVERTISED_HOST`, `CLUSTER_PEERS`, `CLUSTER_GRPC_PORT` | booking-service | enabled, `HOSTNAME`, empty, 9090 |
| `BOOKING_HOLD_DURATION` | booking-service | `15m` |

Each service owns a Postgres schema (`flight`, `booking`, `payment`, `users`, `notification`) in one
database, created and migrated by Flyway on startup. One instance keeps local development simple;
splitting them later is a connection-string change, not a code change.

---

## Tests

```bash
./mvnw test                       # 50 Java tests
cd frontend && npm test           # 8 frontend tests
./start-dev.sh test               # both, plus a type-check
```

The suites concentrate on the logic that is easy to get wrong:

- [`RaftCoordinatorTest`](services/cluster-core/src/test/java/com/skyflow/cluster/RaftCoordinatorTest.java)
  — self-election at quorum 1, one vote per term, refusing a less-current candidate, step-down on a
  newer term, rejecting a stale leader's heartbeat.
- [`TaskDistributionTest`](services/cluster-core/src/test/java/com/skyflow/cluster/TaskDistributionTest.java)
  — dispatch, retry to the attempt limit, capacity refusal, releasing the queue on lost leadership.
- [`BookingServiceTest`](services/booking-service/src/test/java/com/skyflow/booking/service/BookingServiceTest.java)
  — seats released when payment setup fails, duplicate `payment.succeeded` confirming once, a
  payment landing after expiry being refunded, another traveller's booking reading as 404.
- [`JwtServiceTest`](services/common/src/test/java/com/skyflow/common/security/JwtServiceTest.java)
  — access and refresh tokens not interchangeable, foreign signatures and issuers rejected.
- [`JwtAuthenticationFilterTest`](services/api-gateway/src/test/java/com/skyflow/gateway/security/JwtAuthenticationFilterTest.java)
  — a valid token becomes the identity headers, a client-supplied `X-User-Role: admin` is stripped,
  public reads pass but writes to the same path do not.
- [`GatewayRoutesTest`](services/api-gateway/src/test/java/com/skyflow/gateway/GatewayRoutesTest.java)
  — boots the gateway and asserts the routing table loaded, so a YAML typo fails the build rather
  than production.
- [`FlightSearchRequestTest`](services/flight-service/src/test/java/com/skyflow/flight/dto/FlightSearchRequestTest.java)
  — cache-key stability and normalization.
- [`EmailContentFactoryTest`](services/notification-service/src/test/java/com/skyflow/notification/service/EmailContentFactoryTest.java)
  — every event type renders; missing fields are omitted, not printed as `null`.

---

## Deploying

```bash
export AWS_REGION=us-east-1 ECR_REGISTRY=<account>.dkr.ecr.us-east-1.amazonaws.com
export DB_URL=... DB_USER=... DB_PASSWORD=... REDIS_HOST=... RABBITMQ_HOST=... JWT_SECRET=...

cd terraform && terraform init && terraform apply    # VPC, EKS, RDS, ElastiCache, Amazon MQ, ECR
cd .. && ./deploy.sh deploy                          # build, push, apply, wait for rollout
./deploy.sh cluster                                  # which replica is master?
```

`deploy.sh` renders `k8s/secrets.yaml` into a temp directory it deletes on exit, so secret values
never touch the working tree. Images are tagged with the short commit SHA.

> The Terraform in this repo declares modules (`eks`, `rds`, `redis`, `mq`, `alb`, …) that were
> never committed — only `modules/vpc` exists. That gap predates this port; `terraform apply` will
> not work until those modules are written or replaced with community equivalents. The root
> configuration, variables and ECR repository list are correct and current.

---

## Design decisions and trade-offs

**JWT verified once, at the edge.** The gateway verifies the token and injects `X-User-Id`,
`X-User-Email` and `X-User-Role`; downstream services read those headers instead of re-verifying.
That is only safe because two things hold together: the gateway
[strips any client-supplied copy of those headers](services/api-gateway/src/main/java/com/skyflow/gateway/security/JwtAuthenticationFilter.java)
before routing, and a `NetworkPolicy` makes the gateway the only way in. Remove either and
`X-User-Role: admin` becomes a valid request.

**MongoDB dropped.** It held sessions, audit logs, analytics and notifications. Sessions are gone
(tokens are stateless), and audit logs, payment records and notification history are relational
data that is now queried alongside the rows they describe. One less datastore to run, back up and
reason about.

**A user-service was added.** The original React client called `/auth/login`, `/auth/me` and
`/auth/profile` — endpoints that were never implemented server-side. Bookings need an owner, so
that half is now real: BCrypt hashing, HS256 access and refresh tokens, and a login endpoint that
returns the same message for an unknown email and a wrong password so it is not an
account-existence oracle.

**Booking data is denormalized.** A booking stores its own copy of the flight number, route and
times. Reminder emails and "my bookings" then never fan out to `flight-service`, and a later
schedule change cannot silently rewrite what the traveller bought.

**`Instant` and `BigDecimal`, everywhere.** All times are UTC instants, formatted for display at
the edge; the frontend renders UTC deliberately, because a timetable that shifts with the reader's
timezone is worse than useless. Money is `BigDecimal` and converts to Stripe's minor units in
exactly one place.

**The pending task queue is not replicated.** Losing the leader can re-run a task. Handlers are
idempotent instead, which is a far smaller thing to get right than log replication — and the
alternative, a distributed lock, would trade an election for a dependency that has its own
failure modes.

**Checkout stops at the payment intent.** `booking-service` creates the booking and its Stripe
intent in one call and returns the client secret; a Stripe Elements form to confirm it is not
included. The webhook path that reconciles the outcome — and the polling `sync` endpoint the
success page uses when the webhook has not arrived yet — is complete.

**Single Postgres instance, schema per service.** Convenient locally and a defensible starting
point; it is a shared point of failure, and splitting it is a connection-string change when that
matters.

---

## Repository layout

```
pom.xml                  Maven reactor: 2 libraries + 7 services
mvnw                     Maven wrapper (script-only; downloads Maven on first use)
Dockerfile               One image build for every Java service (--build-arg MODULE=…)
docker-compose.yml       Full local stack
docker-compose.cluster.yml   Adds two more booking-service replicas
services/
  common/                Response envelope, errors, events, cache and JWT config
  cluster-core/          cluster.proto, Raft election, master–worker distribution
  api-gateway/           Reactive edge
  user-service/          Accounts and tokens
  flight-service/        Search, inventory, reference data
  booking-service/       Bookings, seat holds, coordination cluster
  payment-service/       Stripe
  notification-service/  RabbitMQ consumer, email templates
  ai-service/            Claude integration
frontend/                React 19 + TypeScript + Vite
k8s/                     Namespace, config, per-service manifests, network policies, ingress
monitoring/              Prometheus scrape config, alert rules, Grafana dashboard
terraform/               AWS infrastructure
deploy.sh                Build, push, apply, roll back
start-dev.sh             Local development
```
