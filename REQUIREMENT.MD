# Practical: Transaction Ingestion & Rule-Based Categorisation Engine

## Scenario

A financial platform receives thousands of raw payment transactions from multiple external sources each day. Each transaction contains a merchant name, amount, currency, and source identifier. Your task is to build a Spring Boot microservice that ingests these transactions in bulk, automatically categorises them using configurable rules, and serves the results via a REST API.

This tests your ability to design clean data models, implement pluggable business logic, and expose well-structured APIs — all core to backend SaaS development.

## What to Build

### Data Model (MySQL + DB Migrations)

- **transactions** — `id`, `merchant_name`, `amount`, `currency`, `transaction_date`, `source` (e.g. `BANK_FEED` | `MANUAL` | `IMPORT`), `created_at`
- **merchant_categories** — `id`, `merchant_name`, `category`, `sub_category`, `confidence_score` (0.0–1.0), `categorised_at`, `strategy_used`
- **category_rules** — `id`, `keyword`, `category`, `sub_category`, `priority`, `is_active`

### REST API

- **POST** `/api/v1/transactions/bulk` — ingest a JSON array of transactions (validate, persist, return summary: accepted / rejected counts)
- **POST** `/api/v1/categorise/trigger` — async trigger to categorise all UNCATEGORISED merchants
- **GET** `/api/v1/transactions` — paginated list; filterable by category, source, date range
- **GET** `/api/v1/analytics/summary` — total spend by category, top 10 merchants by spend, month-over-month delta
- **POST** `/api/v1/rules` — create or update a categorisation rule (admin endpoint)

### Categorisation Logic

Implement a `CategorizationService` using the Strategy design pattern with two strategies:

- **RuleBasedStrategy** — match `merchant_name` against active entries in `category_rules` by keyword (case-insensitive, priority-ordered)
- **FallbackStrategy** — for merchants with no rule match, call an LLM API with a structured prompt and parse a JSON response containing `{ category, sub_category, confidence }`

Strategy selection must be configurable via `application.yml` (e.g. `categorization.fallback.enabled=true`)

Both strategies must persist the `confidence_score` and `strategy_used` to `merchant_categories`

### Technical Requirements

- Spring Boot 3.x, Spring Data JPA, Database migrations (Database schema must be managed using a migration tool of your choice — Flyway, Liquibase, or plain `schema.sql` via Spring Boot's built-in initialisation. Document your choice in the README.), Bean Validation on request DTOs
- Async categorisation using `@Async` with a configurable thread pool (core-size and max-size in properties)
- Global `@ControllerAdvice` with structured error responses: `{ timestamp, status, error, message, path }`
- Unit tests: `CategorizationService` with mocked strategies, edge cases include empty merchant name, unknown currency, duplicate ingestion
- Integration test: full POST `/bulk` → trigger → GET flow using Testcontainers (MySQL)
- Docker Compose: service + MySQL

