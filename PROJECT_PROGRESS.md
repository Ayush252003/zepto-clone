# Project Progress

## Current phase

Phase 12: modular-monolith CI/CD implementation complete. Flyway V1–V8 and the order, payment simulation, delivery, notification, cache, observability, container, and workflow configuration are implemented. The isolated test suite passed after V8. Hosted GitHub CI and deployment cannot execute until this project is pushed to a GitHub remote; the local Docker Compose stack is now running and verified.

## Completed

- Inspected the starter repository and identified it as a plain Java/Gradle project, not an existing Spring Boot backend.
- Defined initial MVP actors, journeys, rules, module boundaries, conceptual data model, and staged roadmap in [docs/project-design.md](docs/project-design.md).
- Established the initial Spring Boot application and documented local run instructions in [README.md](README.md).
- Drafted and implemented the product/category API contract in [docs/catalog-design.md](docs/catalog-design.md).
- Added catalog DTOs, JPA entities/repositories, validation, service rules, REST controllers, centralized API errors, and OpenAPI/Swagger UI support.
- Added a Flyway migration defining MySQL catalog tables with unique constraints, category foreign key, positive-price check, and an active/category index.
- Added unit tests for request validation, page/sort rules, and category deactivation rules.
- Applied Flyway `V1` to the local MySQL 9.6 database; Hibernate schema validation passed on startup.
- Verified health, category/product create, filtered search, duplicate SKU conflict, invalid page size, category deactivation conflict, soft deactivation/reactivation, inactive visibility, Swagger UI, and generated OpenAPI paths.
- Sent eight simultaneous product creates with the same SKU and different categories: one returned `201`, seven returned `409` from the database unique constraint; test products/categories were then soft-deactivated.
- Added customer registration, BCrypt password hashing, login with 15-minute HS256 JWTs, `/api/v1/auth/me`, and ADMIN-only catalog management routes.
- Added Flyway `V2` users table, optional one-time admin bootstrap, security configuration, auth design docs, and local run instructions.
- Applied V2 to MySQL 9.6 and verified registration/login, valid token `/me`, anonymous admin `401`, customer admin `403`, and admin write `201`; temporary auth verification records were removed.
- Bootstrapped the requested administrator locally and verified login plus ADMIN catalog access. Credentials are stored only in local configuration/database, not in source files.
- Added per-product on-hand/reserved inventory, stock management routes, and internal reserve/commit/release lifecycle with Flyway `V3`.
- Added zero-stock inventory initialization for new products and backfilled existing products to zero in V3.
- Verified atomic reservations against MySQL in an isolated `zepto_clone_test` database, including concurrent requests, multi-product rollback, stock-under-reservation rejection, and idempotent finalization.
- Applied V3 to the development database; inventory rows match the existing product count. Verified new product stock starts at zero, ADMIN can set it, anonymous inventory access returns `401`, and CUSTOMER access returns `403`. Temporary HTTP verification records were removed.
- Implemented customer-owned carts with lazy creation, current-price estimates, informational stock, item add/increment/replace/remove, role-based access, and safe per-customer write serialization.
- Added Flyway V4 for carts and cart items, plus isolated MySQL tests covering ownership, inactive products, current pricing, validation, and concurrent increments.
- Added V5–V8 migrations for orders/payments, delivery/notifications, corrected per-order payment idempotency, and source-cart cleanup references.
- Implemented transactional cart checkout with price snapshots, inventory reservation/commit/release, checkout and payment idempotency, pending-order cancellation, payment simulation, order history, and ownership enforcement.
- Implemented delivery status progression and durable in-app notifications, persisted transactionally with the related business state changes.
- Added optional Redis catalog caching, Prometheus metrics, structured JSON logs, and health probes on a loopback-only management listener by default.
- Release review made Redis health conditional (off for the no-cache local default, on in Compose), permitted liveness/readiness endpoints through security, and enabled HTTP histogram buckets for Grafana p95 latency.
- Gradle now compiles with `--release 21`, so JDK 25 local builds are restricted to Java 21 APIs as well as Java 21 bytecode.
- Added a multi-stage non-root Java 21 Docker image, Compose stack for MySQL/Redis/API/Prometheus/Grafana, `.env.example`, image/dependency scanning CI, GHCR publishing, immutable-tag manual deployment, environment approval gates, and rollback/smoke-check logic.
- Added a provisioned Grafana overview dashboard, architecture diagram, and an optional read-only k6 catalog smoke scenario.

## Files changed

- `docs/project-design.md` — requirements, architecture, data model, acceptance checkpoints, and deferred scope.
- `PROJECT_PROGRESS.md` — current milestone record.
- `build.gradle` — Spring Boot plugins, MVC, Actuator, JPA, validation, Flyway, MySQL, OpenAPI, and test dependencies; Java 21 source/target compatibility.
- `src/main/java/com/ayush/zepto/ZeptoApplication.java` — Spring Boot entry point (replaces the IDE console sample).
- `src/main/resources/application.yml` — datasource variables, Hibernate schema validation, port, and health-only Actuator exposure.
- `README.md` — prerequisites, MySQL setup, run/build commands, catalog examples, and API documentation.
- `.idea/misc.xml` and `.idea/gradle.xml` — align IntelliJ project and Gradle JVM with the registered Temurin 25 SDK while keeping Java 21 language/bytecode compatibility.
- `docs/catalog-design.md` — proposed catalog endpoints, data rules, validation, and acceptance criteria.
- `src/main/java/com/ayush/zepto/catalog/` — catalog controllers, DTOs, entities, repositories, query specification, and services.
- `src/main/java/com/ayush/zepto/common/error/` — shared error types and API exception mapping.
- `src/main/resources/db/migration/V1__create_catalog_tables.sql` — initial MySQL schema migration.
- `src/test/java/com/ayush/zepto/catalog/` — validation and service unit tests.
- `src/main/java/com/ayush/zepto/auth/`, `user/`, and `config/` — authentication, user model, and security configuration.
- `src/main/resources/db/migration/V2__create_users_table.sql` — user schema.
- `src/test/java/com/ayush/zepto/auth/AuthServiceTest.java` — authentication service tests.
- `docs/auth-design.md` — auth decisions and endpoint behavior.
- `docs/inventory-design.md` — stock rules, alternatives, API contract, and verification.
- `src/main/java/com/ayush/zepto/inventory/` — stock model, reservation lifecycle, persistence, service, and ADMIN endpoints.
- `src/main/resources/db/migration/V3__create_inventory_tables.sql` — inventory/reservation schema and zero-stock backfill.
- `src/test/java/com/ayush/zepto/inventory/InventoryMysqlIntegrationTest.java` — opt-in isolated MySQL transaction and concurrency tests.
- `src/main/java/com/ayush/zepto/cart/` and `src/main/resources/db/migration/V4__create_cart_tables.sql` — cart API, persistence, and schema.
- `src/test/java/com/ayush/zepto/cart/CartMysqlIntegrationTest.java` — opt-in cart rules and concurrency checks against `zepto_clone_test`.
- `docs/cart-design.md` — cart behavior, API, persistence, concurrency, and verification decisions.
- `src/main/java/com/ayush/zepto/order/`, `delivery/`, and `notification/` — checkout/payment simulation, delivery transitions, and in-app notifications.
- `src/main/resources/db/migration/V5__create_order_payment_tables.sql` through `V8__link_order_items_to_cart_rows.sql` — additive order, delivery, notification, idempotency, and cart-reference schema.
- `src/test/java/com/ayush/zepto/order/OrderMysqlIntegrationTest.java` — opt-in checkout, payment, stock, delivery, notification, and ownership integration tests.
- `Dockerfile`, `compose.yaml`, `.dockerignore`, `.env.example`, and `monitoring/` — container runtime and local metrics/dashboard configuration.
- `.github/workflows/`, `.github/dependabot.yml`, and `gradle.lockfile` — CI, gated deployment, dependency updates, and locked Gradle dependencies.
- `docs/order-payment-design.md`, `docs/delivery-notification-design.md`, `docs/architecture.md`, and `performance/catalog-smoke.js` — feature decisions, runtime topology, and optional performance smoke scenario.

## Technologies learned

- Modular monolith as the starting architecture: one deployable app with explicit feature boundaries, keeping local calls and transactions simple while postponing distributed-system costs.
- Package-by-feature as the initial organization, keeping each business module's responsibilities together.
- Spring Boot startup and auto-configuration: `@SpringBootApplication` enables configuration discovery and component scanning; `SpringApplication.run` starts the application context and embedded web server.
- Actuator provides a standard health endpoint. Only `/actuator/health` is exposed over HTTP.
- Flyway manages schema evolution from the first persisted feature; Hibernate uses `ddl-auto: validate` rather than silently updating tables.
- Product and category removal is soft deactivation to preserve cart/order references. Public reads hide inactive records; admin routes can list and reactivate them.
- Product listing uses an allowlist for sorting and a stable `id` tie-breaker. Substring SQL search is simple but may need replacement if measured query costs grow.
- Stock availability is derived as `on_hand - reserved`; a conditional SQL update makes the availability check and reservation one indivisible database operation.
- Reservation operations use a stable UUID and persisted item snapshots. Multi-product reservations update rows in sorted product-ID order and roll back as one transaction.

## Commands

- Repository/file inspection with PowerShell and `rg`.
- `java -version` — PATH selects Temurin 17.0.19; inspection found JDK 25 also installed.
- Spring Boot 4.1.2 and Gradle 9 compatibility was checked against Spring's official requirements; JDK 25 is used for verification while compiling Java 21 compatible bytecode.

## Verification

- `.\gradlew.bat clean build` — passed with Gradle 9.0 and JDK 25; catalog and auth unit tests passed and the executable jar was packaged.
- Latest `.\gradlew.bat test` with `INVENTORY_MYSQL_TESTS=true` — passed with both cart and inventory MySQL suites enabled (18 tests total).
- IntelliJ project and Gradle XML settings parse correctly; they now select the installed `temurin-25` SDK instead of Java 17.
- Flyway applied and validated migrations V1 through V4; `flyway_schema_history` reports V4 successful and cart tables exist.
- Hibernate started with `ddl-auto: validate` against MySQL 9.6 without schema errors.
- HTTP checks returned: health `200`; category/product create `201`; search `200`; duplicate SKU `409`; invalid page size `400`; category deactivation with active product `409`; product deactivation/reactivation `204`; inactive product hidden publicly and present to admin; Swagger UI `200` and OpenAPI lists nine paths.
- Concurrent uniqueness check returned one create success and seven conflicts. MySQL logs recorded duplicate-key errors, handled as `409` responses by the API.
- `zepto_clone_test` records Flyway V4 as successful; the cart concurrency test preserved all 16 simultaneous increments as one line, and inventory tests allowed exactly 8 reservations from 16 requests against stock of 8.
- Development DB Flyway V4 is successful, and inventory/product counts match. Live checks returned anonymous cart `401`, ADMIN `403`, CUSTOMER empty-cart `200`, add/increment/replace `200` with expected quantities and subtotal, and remove `204`. The generated customer/cart were removed; generated catalog rows were deactivated.
- Temporary verification products/categories remain in the local database in inactive state.
- The pasted IntelliJ error was caused by the literal username `${DB_USERNAME}`; `DB_USERNAME=root`, the local MySQL password, and `JWT_SECRET_BASE64` are set in the local ZeptoApplication run configuration (do not include `${...}`). These values are not source-controlled.
- Flyway warned that MySQL 9.6 is newer than its latest verified MySQL 9.4 version. The migration and validation nevertheless succeeded on 9.6.
- MySQL repository and concurrency integration tests are opt-in and target only `zepto_clone_test`; live HTTP verification targets local `zepto_clone` with narrowly scoped temporary rows.
- The Gradle/JDK 25 process emitted a non-fatal Java restricted native-access warning from Gradle's native-platform library.
- Latest full run: `clean test bootJar` passed with 23 tests, 0 failures/errors, and 1 Redis test skipped because Redis is not installed locally. The opt-in MySQL suites migrated the isolated `zepto_clone_test` database through V8 successfully.
- `gradle.lockfile` was generated with Gradle dependency locking enabled. The Docker build now copies it before resolving dependencies.
- Docker Compose has been built and smoke-verified locally. Trivy image scanning and the GitHub-hosted CI/CD workflows still need a GitHub remote and hosted runner; GHCR publishing and deployment also need repository/environment configuration and secrets.
- Final audit build `clean test bootJar` passed after compatibility fixes: 23 tests, 0 failures, 0 errors, 1 Redis-only test skipped. The MySQL suites were enabled against `zepto_clone_test`.
- Docker Desktop 4.98.0 was installed locally after WSL setup; Docker Engine 29.9.2 and Compose 5.6.1 build and run the complete local stack. MySQL, Redis, API, Prometheus, and Grafana containers all started; MySQL, Redis, and API report healthy.
- Container build completed with the Java 21 Temurin image and produced the executable Spring Boot jar. Flyway reports V8 as the latest successful migration inside the Compose MySQL database.
- Compose smoke checks returned HTTP 200 for catalog and Swagger; Prometheus reports the application target `up`; Grafana health and dashboard search returned 200; two category reads produced a Redis cache key; Prometheus request counters and latency histogram buckets are present.
- Local Compose secrets are generated in ignored `.env` and are not tracked. Compose maps MySQL to host port 3307 to avoid colliding with the separately installed local MySQL service on 3306.
- The final build used JDK 25 with Gradle `options.release=21`; generated application and test bytecode is Java 21 compatible.
- Live JAR smoke against `zepto_clone_test` returned health `UP`, HTTP 200 for health/liveness/readiness and catalog, and Prometheus request-counter and histogram-bucket metrics. The temporary app process was stopped afterward.
- YAML files parsed successfully with the build's SnakeYAML library; the Grafana dashboard JSON parsed with Node. The official Eclipse Temurin 21 Alpine 3.24 base image tags were confirmed present upstream.
- No Git remote is configured. The deployment workflow is manual only and does not deploy automatically. For production, configure the GitHub `production` environment with required reviewers, environment secrets, and branch/tag restrictions before dispatching it.
- The optional k6 scenario targets only `GET /api/v1/categories`, creates no data, and requires an externally installed k6 binary and reachable API to run.

## Architectural decisions

- Start with one store and a modular monolith.
- Use MySQL and Flyway from the first persistent module; don't use Hibernate schema update as the schema owner.
- Enforce category name/slug and product SKU uniqueness case-insensitively in the database; application pre-checks provide friendlier conflict errors.
- Simulated payments only; resolve stock concurrency and payment failure behavior in later milestones.
- `/api/v1/admin/**` routes require an ADMIN bearer token; catalog reads and auth registration/login are public.

## Known issues and limitations

- JDK 21 is not installed; JDK 25 is available. The build targets Java 21 bytecode, and the app needs a Java 21+ runtime. JDK 17 on PATH cannot run Java 21 bytecode.
- MySQL Server 9.6 is installed and was reachable for migration/API verification. Flyway has only verified through MySQL 9.4, so the observed successful 9.6 run is useful but should be treated with that compatibility caveat.
- Docker Desktop requires the Docker engine pipe to be accessed with elevated shell permissions in this environment. It is installed and working; if Docker Desktop is closed, start it before using Compose.
- Git has no commits and contains staged starter files plus a modified `.idea/gradle.xml`; preserve this user state.

## Next milestone

Push the branch to a GitHub repository, configure branch protection and environment secrets/reviewers, then observe the first hosted CI run. After staging infrastructure is available, deploy an immutable image and execute the manual smoke workflow; production release remains explicitly reviewer-gated.

## Interview questions

1. Why should a database unique constraint back up an application-level SKU check?
2. Why soft-deactivate a product instead of physically deleting it?
3. What is the trade-off of using `LIKE '%term%'` search as the catalog grows?
4. Why does cart mutation lock the existing user row before a cart exists?
5. Why is cart availability informational while checkout must reserve inventory?
