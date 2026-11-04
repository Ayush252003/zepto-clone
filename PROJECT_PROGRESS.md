# Project status

The backend is implemented through the local Compose stack and CI configuration. The public repository is [Ayush252003/zepto-clone](https://github.com/Ayush252003/zepto-clone), on `main`.

## What is in the app

- Public product and category reads, with ADMIN catalog management and soft deactivation.
- CUSTOMER registration/login, BCrypt password hashes, short-lived JWTs, and role-based routes.
- Per-product stock, admin stock management, and transactional reservations that use a conditional MySQL update to prevent overselling.
- Customer carts with current-price estimates; checkout rechecks products, snapshots names and prices, and reserves stock.
- Idempotent checkout and simulated payment success, failure, and timeout. Failed or cancelled orders release reservations.
- Delivery status and in-app notifications persisted with their related state changes.
- Flyway schema migrations V1–V8, optional Redis catalog caching, Actuator health and Prometheus metrics.
- Docker Compose for MySQL, Redis, the API, Prometheus, and Grafana; GitHub Actions for verification and a manual, environment-gated deployment workflow.

## Verification record

- The last recorded full local Gradle run passed 23 tests with no failures or errors; one Redis integration test was skipped when Redis was unavailable.
- MySQL integration tests were run against the isolated `zepto_clone_test` database through Flyway V8.
- The local Compose stack was previously built and smoke-checked: API, MySQL, Redis, Prometheus, and Grafana started; health, catalog, Swagger, cache, and metrics checks succeeded.
- The GitHub Actions workflow has been pushed to the public repository. An initial run failed because `gradlew` was not executable in Git. Commit `c463059` fixes the mode. Confirm the latest result on the [Actions page](https://github.com/Ayush252003/zepto-clone/actions) before relying on hosted CI; a queued run was observed after that fix.

These results are from separate runs and environments; they are not a claim that every integration has passed in hosted CI.

## Current limitations

- Payments and delivery are simulations. There is no external payment, email/SMS, or courier integration.
- The application models one store/service area. Redis is a cache, not a source of truth.
- Login throttling, refresh tokens, token revocation, and production hosting are out of scope.
- Flyway succeeded on the local MySQL 9.6 installation, while the CI service uses MySQL 8.4.13. The local Flyway warning about 9.6 being newer than its verified range should be revisited if that version is used in production.
- Deployment needs a host, GitHub environment secrets, and required reviewers. No deployment has been run.

## Useful checks

- API documentation: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8081/actuator/health`
- CI and release workflows: [GitHub Actions](https://github.com/Ayush252003/zepto-clone/actions)
- Architecture: [docs/architecture.md](docs/architecture.md)
