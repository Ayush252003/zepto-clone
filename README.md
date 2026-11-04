# Zepto Clone Backend

A learning project for a Zepto inspired quick commerce backend. The first release is planned as a single-store modular monolith; see [the project design](docs/project-design.md) for scope and architecture.

## Prerequisites

- JDK 21 or later. The code is compiled for Java 21. The current development machine has JDK 25 available; use it to run Gradle if JDK 21 is not installed.
- MySQL 8.0.16 or later, with a `zepto_clone` database and a local user allowed to create tables and indexes. Flyway creates and updates catalog, users, inventory, carts, orders, payments, deliveries, and notifications.
- Git

The Gradle Wrapper downloads and selects the project's Gradle version. No global Gradle installation is needed.

Create a local database using MySQL:

```sql
CREATE DATABASE zepto_clone CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Set `DB_URL` (defaults to `jdbc:mysql://localhost:3306/zepto_clone`), `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64` in the environment. The database username/password and JWT signing key have no source-controlled defaults. Generate a fresh key locally with PowerShell: `$bytes = [byte[]]::new(32); [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes); [Convert]::ToBase64String($bytes)`.

## Run locally

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21-or-later'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_USERNAME = 'your-local-mysql-user'
$env:DB_PASSWORD = 'your-local-mysql-password'
$env:JWT_SECRET_BASE64 = 'your-generated-base64-secret'
.\gradlew.bat bootRun
```

On macOS or Linux:

```shell
./gradlew bootRun
```

On macOS/Linux, export `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64` before starting the app.

To create the initial administrator, set both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` before the first startup. The application creates an ADMIN only if that email is not already registered; it will not promote a customer account. Leave both unset for normal starts after bootstrap.

Make sure `JAVA_HOME` points to JDK 21 or later before running the wrapper. On the current development machine, JDK 25 is installed, while PATH defaults to JDK 17.

The application API listens on port `8080`. Management endpoints use a separate local-only port `8081`. Check health at `http://localhost:8081/actuator/health`:

```shell
curl http://localhost:8081/actuator/health
```

Expected response:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

Catalog reads and health are public. Registration and login are public, while `/api/v1/auth/me` requires a Bearer token. Every `/api/v1/admin/**` route requires an ADMIN token. Cart, orders, delivery lookup, and notifications require a CUSTOMER token.

Once the application is connected to MySQL, interactive API documentation is available at `http://localhost:8080/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`.

## Catalog API quick check

With the app running and an admin bootstrapped, register a customer or log in as the administrator, then pass the returned access token for catalog writes:

In Windows PowerShell, use `curl.exe` for the commands below so they do not resolve to PowerShell's `curl` alias.

```shell
curl -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"admin@example.com","password":"your-admin-password"}'
curl -i -X POST http://localhost:8080/api/v1/admin/categories -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d '{"name":"Fruits","slug":"fruits"}'
curl -i -X POST http://localhost:8080/api/v1/admin/products -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d '{"sku":"FRUIT-BAN-001","name":"Banana","description":"Fresh bananas","categoryId":1,"price":49.00}'
curl "http://localhost:8080/api/v1/products?q=ban&page=0&size=20&sort=price,asc"
```

Expected statuses are `200 OK` for login, `201 Created` for category/product creation, and `200 OK` for the public product list. Product list responses include `content`, page/size, total element/page counts, and first/last flags. Duplicate SKUs/slugs/emails return `409`; invalid fields and paging values return `400`; a missing product/category returns `404`; invalid credentials return `401`; a customer attempting an admin route receives `403`. Flyway records migration state in `flyway_schema_history` and creates `categories`, `products`, `users`, and inventory/reservation tables.

## Inventory API quick check

Every new product starts with zero stock. An ADMIN can set and inspect its physical stock total; already-reserved units cannot be undercut:

```shell
curl -i -X PUT http://localhost:8080/api/v1/admin/products/1/inventory -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" -d '{"onHand":25}'
curl -i http://localhost:8080/api/v1/admin/products/1/inventory -H "Authorization: Bearer <accessToken>"
```

The response contains `onHand`, `reserved`, and derived `available`. Negative stock returns `400`; setting stock below an existing reservation returns `409`; a customer gets `403` and an anonymous request gets `401`. Reservation, commit, and release operations are internal checkout/payment service operations, not public routes.

## Cart API quick check

Cart routes require a CUSTOMER Bearer token. The cart estimates current prices and availability; adding an item does not reserve stock.

```shell
curl -i http://localhost:8080/api/v1/cart -H "Authorization: Bearer <customerToken>"
curl -i -X POST http://localhost:8080/api/v1/cart/items -H "Authorization: Bearer <customerToken>" -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
curl -i -X PUT http://localhost:8080/api/v1/cart/items/1 -H "Authorization: Bearer <customerToken>" -H "Content-Type: application/json" -d '{"quantity":3}'
curl -i -X DELETE http://localhost:8080/api/v1/cart/items/1 -H "Authorization: Bearer <customerToken>"
```

GET returns an empty cart before the first add. POST increments an existing product line; PUT replaces its quantity. The response includes current product details/prices, line totals, subtotal, active state, and informational `availableNow` stock. Cart items do not snapshot prices; checkout recalculates and reserves stock.

## Orders and simulated payment

Checkout requires a customer Bearer token and an `Idempotency-Key` header. The request body is empty because checkout reads the authenticated customer's persisted cart. It returns `201` with immutable price/name snapshots and `PENDING_PAYMENT` status. Replaying the same key for that customer returns the same order. Insufficient stock or inactive cart products return `409` without creating an order.

```shell
curl -i -X POST http://localhost:8080/api/v1/orders -H "Authorization: Bearer <customerToken>" -H "Idempotency-Key: checkout-2026-001"
curl -i -X POST http://localhost:8080/api/v1/orders/1/payment -H "Authorization: Bearer <customerToken>" -H "Idempotency-Key: payment-2026-001" -H "Content-Type: application/json" -d '{"outcome":"SUCCESS"}'
curl -i http://localhost:8080/api/v1/orders -H "Authorization: Bearer <customerToken>"
curl -i -X POST http://localhost:8080/api/v1/orders/2/payment -H "Authorization: Bearer <customerToken>" -H "Idempotency-Key: payment-2026-002" -H "Content-Type: application/json" -d '{"outcome":"FAILURE"}'
```

Payment outcomes are simulations only: `SUCCESS` commits reserved inventory; `FAILURE` and `TIMEOUT` cancel the order and release it. No real payment details are accepted. A pending order can be cancelled with `POST /api/v1/orders/{id}/cancel`.

## Delivery and notifications

A successful payment creates a simulated delivery with a 30-minute ETA. Customers can read their own progress and in-app notifications. Admins advance one delivery state at a time; assigning requires a courier display name.

```shell
curl -i http://localhost:8080/api/v1/orders/1/delivery -H "Authorization: Bearer <customerToken>"
curl -i -X PATCH http://localhost:8080/api/v1/admin/deliveries/1 -H "Authorization: Bearer <adminToken>" -H "Content-Type: application/json" -d '{"status":"ASSIGNED","courierName":"Courier A","etaMinutes":25}'
curl -i http://localhost:8080/api/v1/notifications -H "Authorization: Bearer <customerToken>"
curl -i -X PATCH http://localhost:8080/api/v1/notifications/1/read -H "Authorization: Bearer <customerToken>"
```

Progression is `PREPARING → ASSIGNED → PACKED → OUT_FOR_DELIVERY → DELIVERED`. Invalid/skipped transitions return `409`. Notifications are durable in-app records written in the same MySQL transaction as order, payment, or delivery state changes; no email/SMS provider or Kafka is used.

## MySQL integration tests

The standard test task skips database/Redis integration tests. To run opt-in MySQL transaction tests, create a separate `zepto_clone_test` database and set `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64`. Set `INVENTORY_MYSQL_TESTS=true` to run inventory, cart, and order/delivery integration tests. Set `REDIS_CACHE_TESTS=true` and `CACHE_TYPE=redis` when Redis is available. GitHub Actions starts disposable MySQL and Redis services and runs these tests.

```powershell
$env:INVENTORY_MYSQL_TESTS = 'true'
.\gradlew.bat test
```

To include the Redis cache integration test, also set `REDIS_CACHE_TESTS=true` and `CACHE_TYPE=redis` when a Redis server is available at `REDIS_HOST` (localhost by default).

These tests use only `zepto_clone_test`; do not point them at the development database. The Redis cache test creates and removes only uniquely named temporary catalog rows.

## Build

```shell
./gradlew build
```

On Windows, run `.\gradlew.bat build` instead.

## Docker and local observability

Docker Compose starts MySQL, Redis, the API, Prometheus, and Grafana. The application defaults to no cache for a plain local IntelliJ run and disables the optional Redis health check; Compose opts into Redis caching and enables its health check for active product details and category lists. Product/category writes evict affected cache entries. Management endpoints bind to loopback on port `8081` by default; Compose exposes that port only to its internal monitoring network.

```powershell
Copy-Item .env.example .env
# Edit .env and replace all placeholder passwords and the JWT key.
docker compose up --build -d
docker compose ps
```

The API is at `http://localhost:8080`, health at `http://localhost:8081/actuator/health`, Prometheus at `http://localhost:9090`, and Grafana at `http://localhost:3000`. MySQL maps to `3307` by default to avoid colliding with an existing local MySQL server. Named volumes persist database, cache, and monitoring data. `docker compose down -v` permanently removes those local volumes and their data.

## GitHub Actions CI/CD

`.github/workflows/ci.yml` runs on pull requests and pushes to `main`/`master` and version tags. A GitHub-hosted Ubuntu runner starts disposable MySQL/Redis services, configures Java 21 and Gradle, runs unit and integration tests, uploads test reports, builds the Docker image, and scans it for high/critical vulnerabilities. Pushes to the default branch or a version tag publish an immutable `sha-<commit>` image to GHCR; default-branch pushes also update `latest`.

`.github/workflows/deploy.yml` is manual and requires an immutable image tag. It can target GitHub's `staging` or `production` environment, deploys over SSH, verifies the host key, checks container health, and restores the prior image if the smoke test fails. Configure `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, and `DEPLOY_PATH` separately in each environment. The host must have Docker Compose, the project Compose files and its own `.env`, and read access to GHCR. Configure required reviewers and branch/tag restrictions on both GitHub environments; the production environment approval gates the production job.

For a read-only catalog performance smoke against a reachable API, install k6 and run `k6 run performance/catalog-smoke.js` (optionally set `BASE_URL`). It uses five virtual users for 30 seconds and checks p95 under 500 ms and errors under 1%; treat this as a smoke baseline, not a capacity benchmark.

Dependency Review checks pull request dependency changes; Dependabot proposes weekly updates for Gradle, GitHub Actions, and container images. Once a GitHub remote is added and the branch pushed, require the `verify` and `dependency-review` checks in branch protection. This workspace has no Git remote, so hosted workflow runs cannot be launched from this machine yet.

## IntelliJ troubleshooting

If IntelliJ reports `invalid source release: 21`, its compiler or Gradle JVM is older than Java 21. This project uses the registered Temurin 25 SDK to run Gradle and the application, while Gradle emits Java 21 compatible bytecode. After opening/reloading the project, confirm **Project SDK** and **Gradle JVM** are set to Temurin 25 and the language level remains Java 21.

If startup says MySQL denied user `'${DB_USERNAME}'`, the run configuration is passing the placeholder literally. Open **Run > Edit Configurations > ZeptoApplication > Environment variables** and set `DB_USERNAME=root` (or your local application database user) and `DB_PASSWORD` to the matching local password. Enter the value directly; do not include `${...}`. Keep the password in the IDE run configuration or environment, never in source files.

## Current milestone

Monolithic backend through CI/CD: catalog, auth, inventory, carts, orders, simulated payments, delivery, in-app notifications, optional Redis caching, Prometheus metrics, Compose, GitHub Actions CI/GHCR publishing, and manual staging/production CD gated by environment protection. Microservices and managed cloud infrastructure remain later stages.

See [docs/architecture.md](docs/architecture.md) for the runtime diagram and [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) for implementation and verification status.

