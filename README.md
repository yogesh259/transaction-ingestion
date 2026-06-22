# Transaction Ingestion & Rule-Based Categorisation Engine

A Spring Boot 3 microservice that ingests payment transactions in bulk, categorises
merchants using configurable rules with an LLM fallback (Strategy pattern), and serves
the results via a REST API.

> Requirements: [`REQUIREMENT.MD`](./REQUIREMENT.MD)

---

## Contents
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Running the service](#running-the-service)
- [Database migrations (Flyway) — and why](#database-migrations-flyway--and-why)
- [Configuration reference](#configuration-reference)
- [API reference & examples](#api-reference--examples)
- [Categorisation logic](#categorisation-logic)
- [Error handling](#error-handling)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Implementation notes](#implementation-notes)
- [Limitations](#limitations)

---

## Tech stack

| Concern        | Choice                                              |
|----------------|-----------------------------------------------------|
| Language       | Java 21 (LTS)                                        |
| Framework      | Spring Boot 3.5.x (Web, Data JPA, Validation)       |
| Database       | MySQL 8                                              |
| Migrations     | **Flyway** (versioned SQL)                           |
| Async          | `@Async` + configurable `ThreadPoolTaskExecutor`    |
| API docs       | springdoc-openapi (Swagger UI)                      |
| Tests          | JUnit 5, Mockito, Testcontainers (MySQL), Awaitility|
| Packaging      | Maven · multi-stage Docker · docker-compose         |

## Architecture

Layered: **Controller → Service → Repository → MySQL**, with a pluggable
**Strategy**-based categorisation engine and a mockable **LLM client**.

```
Controller (REST, DTO validation)
   └─ Service (business logic, @Async, @Transactional, Strategy orchestration)
        ├─ Repository (Spring Data JPA)            → MySQL (schema owned by Flyway)
        └─ LlmClient (RestClient → external LLM)   → External LLM (mockable)
```


## Prerequisites

**Option A — Docker only (recommended, nothing else needed):**
- Docker Desktop / Docker Engine with Compose

**Option B — Local build/run:**
- **JDK 21** (this repo targets Java 21; it will *not* compile on Java 11/17-)
- Maven 3.9+ **or** the committed wrapper `./mvnw` (script-only; downloads Maven on first use)
- A running MySQL 8 (or use the `mysql` service from compose)

## Running the service

### With Docker Compose (service + MySQL)

```bash
docker compose up --build
```

This builds the app image, starts MySQL, waits for it to be healthy, runs Flyway
migrations on startup, and exposes the API on **http://localhost:8080**.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Tear down (and wipe the DB volume):

```bash
docker compose down -v
```

### Locally (against a MySQL on localhost:3306)

```bash
# point to your DB (defaults: root/root, db auto-created)
mvn spring-boot:run
```

## Database migrations (Flyway) — and why

**Choice: Flyway**, with versioned SQL scripts in
[`src/main/resources/db/migration`](./src/main/resources/db/migration):

| Script | Purpose |
|--------|---------|
| `V1__create_transactions.sql`        | `transactions` + indexes |
| `V2__create_merchant_categories.sql` | `merchant_categories` + unique merchant constraint + confidence CHECK |
| `V3__create_category_rules.sql`      | `category_rules` + unique keyword |
| `V4__seed_category_rules.sql`        | seed starter rules |

**Why Flyway (over `ddl-auto=update` or `schema.sql`):**
- **Reproducible & versioned** — the schema is recreated identically in every
  environment (dev, Testcontainers, Docker, prod) and reviewed in Git like code.
- **Production-safe** — Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate`,
  so it **never** creates or alters tables; it only validates the entities against the
  Flyway-managed schema and fails fast on drift.
- **Simple & MySQL-native** — plain SQL, ordered versions, and a metadata table that
  tracks what has been applied.

> If your MySQL/driver combination rejects strict validation, set
> `spring.jpa.hibernate.ddl-auto=none` — Flyway remains the single source of truth.

## Configuration reference

All keys live in [`application.yml`](./src/main/resources/application.yml) and are
environment-overridable.

| Property | Default | Description |
|----------|---------|-------------|
| `categorization.max-bulk-size` | `1000` | Max rows per `POST /bulk` (larger ⇒ **413**) |
| `categorization.fallback.enabled` | `true` | Enable/disable the LLM fallback strategy |
| `categorization.async.core-size` | `4` | Async pool core threads |
| `categorization.async.max-size` | `8` | Async pool max threads (`>= core-size`, validated) |
| `categorization.async.queue-capacity` | `100` | Async queue capacity |
| `categorization.async.thread-name-prefix` | `cat-` | Async thread name prefix |
| `categorization.llm.base-url` | `https://api.openai.com/v1` | LLM API base URL |
| `categorization.llm.api-key` | _(empty)_ | LLM API key; blank ⇒ fallback disabled |
| `categorization.llm.model` | `gpt-4o-mini` | LLM model name |
| `categorization.llm.timeout-seconds` | `15` | LLM connect/read timeout |
| `spring.data.web.pageable.default-page-size` | `20` | Default `GET /transactions` page size |
| `spring.data.web.pageable.max-page-size` | `200` | Maximum page size |
| `DB_USERNAME` / `DB_PASSWORD` | `root`/`root` | DB credentials (env) |

All `categorization.*` keys bind to a single validated `CategorisationProperties` record
(fails fast at startup on invalid values, e.g. `max-size < core-size`).
Health endpoints **`/actuator/health`** and `/actuator/info` are exposed.

## API reference & examples

Base path: `/api/v1`. Replace `localhost:8080` as needed.

### 1. Bulk ingest — `POST /transactions/bulk`
Per-row validation: valid rows are persisted, invalid ones rejected with index + reason.

```bash
curl -X POST http://localhost:8080/api/v1/transactions/bulk \
  -H "Content-Type: application/json" \
  -d '[
        {"merchantName":"STARBUCKS NYC","amount":5.50,"currency":"USD","transactionDate":"2026-05-01","source":"BANK_FEED"},
        {"merchantName":"UBER TRIP 88","amount":20.00,"currency":"USD","transactionDate":"2026-06-02","source":"IMPORT"},
        {"merchantName":"","amount":-3,"currency":"XYZ","transactionDate":"2026-06-03","source":"MANUAL"}
      ]'
```
```json
{ "acceptedCount": 2, "rejectedCount": 1,
  "errors": [ { "index": 2, "reason": "amount: must be positive; currency: must be a valid ISO-4217 currency code; merchantName: must not be blank" } ] }
```

### 2. Trigger categorisation — `POST /categorise/trigger`
Async; returns **202 Accepted** immediately.

```bash
curl -i -X POST http://localhost:8080/api/v1/categorise/trigger
```
```json
{ "status": "ACCEPTED", "message": "Categorisation of uncategorised merchants has been triggered." }
```

### 3. List transactions — `GET /transactions`
Paginated; filter by `category`, `source`, `dateFrom`, `dateTo`; standard `page`/`size`/`sort`.

```bash
curl "http://localhost:8080/api/v1/transactions?category=Transport&source=IMPORT&dateFrom=2026-06-01&dateTo=2026-06-30&page=0&size=20"
```

### 4. Analytics summary — `GET /analytics/summary`

```bash
curl http://localhost:8080/api/v1/analytics/summary
```
```json
{
  "totalSpendByCategory": [ { "category": "Transport", "totalSpend": 20.00 } ],
  "topMerchants":         [ { "merchantName": "UBER TRIP 88", "totalSpend": 20.00 } ],
  "monthOverMonthDelta":  [ { "month": "2026-05", "totalSpend": 5.50, "previousMonthSpend": null, "deltaPct": null },
                            { "month": "2026-06", "totalSpend": 20.00, "previousMonthSpend": 5.50, "deltaPct": 263.64 } ]
}
```

### 5. Create/update rule — `POST /rules` (admin)
Upsert by keyword (case-insensitive).

```bash
curl -X POST http://localhost:8080/api/v1/rules \
  -H "Content-Type: application/json" \
  -d '{"keyword":"TESCO","category":"Shopping","subCategory":"Groceries","priority":25,"isActive":true}'
```

## Categorisation logic

`CategorisationService` (orchestrator) + `MerchantCategoriser` (per-merchant
transactional unit) implement the **Strategy pattern**:

1. **`RuleBasedStrategy`** — matches `merchant_name` against **active** `category_rules`
   by keyword, **case-insensitive** and **priority-ordered** (lowest value wins).
   Deterministic ⇒ `confidence = 1.0`, `strategy_used = RULE_BASED`. Matching is
   **substring-based**.
2. **`FallbackStrategy`** — only when no rule matches **and**
   `categorization.fallback.enabled=true`. Calls the LLM with a structured prompt and
   parses `{ category, sub_category, confidence }` (confidence **clamped to [0,1]**) ⇒
   `strategy_used = FALLBACK_LLM`.

How a trigger run works:

- Active rules are loaded **once per run** and reused for every merchant.
- The trigger is a single `@Async` method on the configurable pool
  (`categorization.async.*`) that processes the uncategorised merchants in turn.
- Each merchant is persisted via an **atomic `INSERT … ON DUPLICATE KEY UPDATE`**
  upsert (one row per merchant) by `MerchantCategoriser`, its own `@Transactional` bean.

When the LLM key is absent or a call fails, the fallback resolves to empty and the
merchant is left uncategorised.

## Error handling

A global `@RestControllerAdvice` returns a consistent body for all errors:

```json
{ "timestamp": "2026-06-22T10:15:30Z", "status": 400, "error": "Bad Request",
  "message": "currency: must be a valid ISO-4217 currency code", "path": "/api/v1/rules" }
```

Covers validation (400), malformed JSON / type mismatch (400), oversized bulk batch
(**413**), and a safe catch-all (500, full stack logged server-side).

## Testing

```bash
mvn test                 # all tests (integration test needs Docker)
mvn test '-Dtest=!TransactionFlowIntegrationTest'   # unit + web only, no Docker
```

- **Unit / web tests (46, no DB):** `MerchantCategoriserTest` &
  `CategorisationServiceTest` (mocked strategies + orchestration), `RuleBasedStrategyTest`,
  `FallbackStrategyTest`, `TransactionServiceTest`, `AnalyticsServiceTest`
  (month-over-month incl. gap + year boundary), `RuleServiceTest`,
  `IsoCurrencyValidatorTest`, `RestClientLlmClientParseTest` (LLM JSON parsing + confidence
  clamping), `GlobalExceptionHandlerTest` (structured body), and `@WebMvcTest` slices for
  the controllers. Edge cases covered: **empty merchant name**, **unknown currency**,
  **duplicate ingestion**, oversized batch, fallback on/off, priority ordering.
- **Integration test** (`TransactionFlowIntegrationTest`): spins up **MySQL via
  Testcontainers**, runs Flyway, and exercises the full
  `POST /bulk → POST /categorise/trigger → GET /transactions → GET /analytics/summary`
  flow (LLM mocked), asserting the **persisted strategy/confidence** on typed responses.
  **Requires Docker to be running.**

## Project structure

```
src/main/java/com/example/ingestion/
├── config/      AsyncConfig, CategorisationProperties, OpenApiConfig
├── controller/  Transaction, Categorisation, Analytics, Rule
├── service/     TransactionService, CategorisationService, MerchantCategoriser, AnalyticsService, RuleService
├── strategy/    CategorisationStrategy, RuleBasedStrategy, FallbackStrategy, CategorisationResult
├── llm/         LlmClient, RestClientLlmClient, LlmCategorisation
├── repository/  + projection/ (analytics)
├── entity/      Transaction, MerchantCategory, CategoryRule, Source, StrategyUsed
├── dto/         request/ + response/
├── mapper/ · spec/ · validation/ · exception/
src/main/resources/
├── application.yml · application-docker.yml
└── db/migration/  V1..V4
```

## Implementation notes

- **Bulk ingest** validates each row independently: valid rows are persisted and invalid
  rows are returned with their index and reason; a batch larger than
  `categorization.max-bulk-size` is rejected with **413**.
- **Per-merchant categorisation** runs in `MerchantCategoriser` (its own `@Transactional`
  bean) and persists through an atomic `INSERT … ON DUPLICATE KEY UPDATE`, so re-running a
  trigger updates the existing row rather than creating duplicates.
- **LLM client** is generic, configurable and mockable; with no API key it resolves to
  empty and the merchant is left uncategorised. Confidence is clamped to `[0,1]`.
- **Analytics** — `totalSpendByCategory` includes an explicit `Uncategorised` bucket;
  month-over-month compares each month with the immediately preceding calendar month.
- **Pagination** is returned through a `PagedResponse` envelope.
- **`merchant_categories` ↔ `transactions`** are linked by `merchant_name`.

## Limitations

- **Authentication/authorisation** is not implemented; `POST /rules` and
  `POST /categorise/trigger` are open. A production deployment would protect the admin
  and trigger routes (API key, basic auth, or gateway auth).
- **The LLM client has no retry, circuit breaker or connection pooling** — only request
  timeouts with graceful degradation.
- **Analytics aggregates and the merchant upsert use MySQL-specific SQL**
  (`YEAR()`/`MONTH()`, `LIMIT`, `INSERT … ON DUPLICATE KEY UPDATE`).
- **Rule matching is substring-based**, which can over-match; keep keywords specific.


