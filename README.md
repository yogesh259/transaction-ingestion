# Transaction Ingestion & Rule-Based Categorisation Engine

A Spring Boot 3 microservice that ingests payment transactions in bulk, categorises
merchants using configurable rules with an LLM fallback (Strategy pattern), and serves
the results via a REST API.

> Requirements: [`REQUIREMENT.MD`](./REQUIREMENT.MD)

---

## Contents
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Running the service](#running-the-service)
- [Database migrations (Flyway)](#database-migrations-flyway)
- [API reference](#api-reference)
- [Categorisation logic](#categorisation-logic)

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

Tear down (and wipe the DB volume):

```bash
docker compose down -v
```

## Database Migrations (Flyway)

The database schema is managed by Flyway using versioned SQL scripts located in
[`src/main/resources/db/migration`](./src/main/resources/db/migration):

| Script | Purpose |
|--------|---------|
| `V1__create_transactions.sql`        | `transactions` + indexes |
| `V2__create_merchant_categories.sql` | `merchant_categories` + unique merchant constraint + confidence CHECK |
| `V3__create_category_rules.sql`      | `category_rules` + unique keyword |
| `V4__seed_category_rules.sql`        | seed starter rules |

## API Reference

Base path: `/api/v1`. The Swagger UI provides a full, interactive reference for all endpoints.

### 1. Bulk ingest — `POST /transactions/bulk`
Per-row validation: valid rows are persisted, invalid ones are rejected with their index and a reason.

**Request Body:**
```json
[
  {"merchantName":"STARBUCKS NYC","amount":5.50,"currency":"USD","transactionDate":"2026-05-01","source":"BANK_FEED"},
  {"merchantName":"UBER TRIP 88","amount":20.00,"currency":"USD","transactionDate":"2026-06-02","source":"IMPORT"},
  {"merchantName":"","amount":-3,"currency":"XYZ","transactionDate":"2026-06-03","source":"MANUAL"}
]
```

### 2. Trigger categorisation — `POST /categorise/trigger`
Asynchronously triggers the categorisation of all uncategorised merchants. Returns **202 Accepted** immediately.

### 3. List transactions — `GET /transactions`
Paginated list of transactions. Supports filtering by `category`, `source`, `dateFrom`, `dateTo`, and standard pagination (`page`/`size`/`sort`).

### 4. Analytics summary — `GET /analytics/summary`
Provides an analytics summary including total spend by category, top merchants, and month-over-month spending deltas.

### 5. Create/update rule — `POST /rules` (admin)
Upserts a categorisation rule based on a keyword (case-insensitive).

**Request Body:**
```json
{
  "keyword":"TESCO",
  "category":"Shopping",
  "subCategory":"Groceries",
  "priority":25,
  "isActive":true
}
```

## Categorisation logic

`CategorisationService` (orchestrator) + `MerchantCategoriser` (per-merchant
transactional unit) implement the **Strategy pattern**:

1.  **`RuleBasedStrategy`** — matches `merchant_name` against **active** `category_rules`
    by keyword, **case-insensitive** and **priority-ordered** (lowest value wins).
    Deterministic ⇒ `confidence = 1.0`, `strategy_used = RULE_BASED`. Matching is
    **substring-based**.
2.  **`FallbackStrategy`** — only when no rule matches **and**
    `categorization.fallback.enabled=true`. Calls the LLM with a structured prompt and
    parses `{ category, sub_category, confidence }` (confidence **clamped to [0,1]**) ⇒
    `strategy_used = FALLBACK_LLM`.

How a trigger run works:

- Active rules are loaded **once per run** and reused for every merchant.
- The trigger is an `@Async` method on the configurable pool
  (`categorization.async.*`) that categorises the uncategorised merchants **in
  parallel** across the pool (`max-size` bounds the parallelism).
- Each merchant is persisted via an **atomic `INSERT … ON DUPLICATE KEY UPDATE`**
  upsert (one row per merchant) by `MerchantCategoriser`, its own `@Transactional` bean.

When the LLM key is absent or a call fails, the fallback resolves to empty and the
merchant is left uncategorised.
