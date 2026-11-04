# Zepto Clone Backend

A single-store quick-commerce API built as a Spring Boot modular monolith. It covers a product catalog, customer accounts, stock reservations, carts, checkout, simulated payments, delivery status, and in-app notifications. The project is for learning and local experimentation; it does not connect to a real payment or courier service.

## Run the stack

Requirements: Docker Desktop with Compose, and enough memory for MySQL, Redis, the API, Prometheus, and Grafana.

```powershell
Copy-Item .env.example .env
```

Replace every sample value in `.env`, especially the passwords and JWT key. Generate a key in PowerShell:

```powershell
$bytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Start the services:

```powershell
docker compose up --build -d
docker compose ps
```

The API is available at `http://localhost:8080`; Swagger UI is at `http://localhost:8080/swagger-ui.html`. Prometheus and Grafana use ports 9090 and 3000. Compose maps MySQL to 3307 so it can coexist with a local server on 3306. The management port is internal to the Compose network; `docker compose ps` shows whether the app's health check is passing. When running the app directly on your machine, health is at `http://localhost:8081/actuator/health`. Named volumes keep database and monitoring data between restarts. To remove the stack but keep data, run `docker compose down`; `docker compose down -v` also deletes the volumes.

The initial administrator is optional. To create one at first startup, set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env` before starting the app. The bootstrap does not change an existing account. Remove those values after the account has been created.

## Run the API without Compose

Use JDK 21 or newer, MySQL, and a Base64-encoded JWT key of at least 32 bytes. Create the database first:

```sql
CREATE DATABASE zepto_clone CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Set `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64`, then run `./gradlew bootRun` (Windows: `gradlew.bat bootRun`). The app applies Flyway migrations on startup and validates the resulting schema with Hibernate. Redis is optional; a plain local run uses no cache.

## API areas

All application routes use the `/api/v1` prefix.

| Area | Routes | Access |
| --- | --- | --- |
| Authentication | `/auth/register`, `/auth/login`, `/auth/me` | Register/login are public; `/me` needs a token |
| Catalog | `/products`, `/categories` | Reads are public |
| Catalog administration | `/admin/products`, `/admin/categories` | ADMIN |
| Inventory | `/admin/products/{id}/inventory` | ADMIN |
| Cart | `/cart`, `/cart/items` | CUSTOMER |
| Orders and payment simulation | `/orders`, `/orders/{id}/payment` | CUSTOMER |
| Delivery and notifications | `/orders/{id}/delivery`, `/notifications` | CUSTOMER; delivery updates are ADMIN |

Registration creates a CUSTOMER account. Admin accounts are provisioned through the startup environment variables. Passwords are BCrypt-hashed and login returns a short-lived JWT. Cart prices and stock are estimates; checkout rechecks the cart and atomically reserves stock. Orders keep purchase-time product and price snapshots. Payment outcomes are simulated and accept no payment details.

## Tests and build

```shell
./gradlew clean test bootJar
```

MySQL transaction tests are opt-in and use the separate `zepto_clone_test` database. Set `INVENTORY_MYSQL_TESTS=true`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET_BASE64` to include them. The Redis integration test also needs Redis, `REDIS_CACHE_TESTS=true`, and `CACHE_TYPE=redis`. Do not point integration tests at the development database.

GitHub Actions runs the build and tests with temporary MySQL and Redis services, validates Compose, builds the container, and scans it for high and critical vulnerabilities. GHCR publishing is limited to version tags. The staging/production deployment workflow is manual and requires protected GitHub environments plus host-specific secrets; it does not run as part of ordinary CI.

See [the runtime architecture](docs/architecture.md) and [the implementation record](PROJECT_PROGRESS.md) for design decisions and verification notes. CI results are available in [GitHub Actions](https://github.com/Ayush252003/zepto-clone/actions).
