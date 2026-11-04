# Zepto Inspired Quick Commerce: MVP Requirements and Design

## Scope

The MVP is a single Spring Boot application for one store/service area. Customers can browse the catalog, manage a cart, place an order, use simulated payment, and view basic delivery status. Administrators manage categories, products, prices, and stock. The MVP does not collect real payments or attempt live courier dispatch.

Starting with one store keeps stock ownership and delivery eligibility understandable. The domain can add store identifiers when multi-store behavior becomes a real requirement.

## Users and permissions

| Role | Allowed actions |
| --- | --- |
| Customer | Register and sign in; browse products; manage their own cart; place orders; view their own orders and delivery status |
| Administrator | Create and update categories and products; set product prices and manage stock |

Authentication is implemented: customers use registration/login and Bearer tokens, and `/api/v1/admin/**` routes require an ADMIN role. Cart, order, and delivery authorization will be enforced as those modules are added.

## MVP user stories

1. As a visitor, I can browse active products by category and search by product name.
2. As a customer, I can add, change, and remove products in my cart and see an estimated total.
3. As a customer, I can place an order only when requested products are active and available.
4. As a customer, I can see the order's item names, quantities, and prices as they were at purchase time.
5. As a customer, I can see whether simulated payment succeeded or failed and whether my order is progressing or cancelled.
6. As an administrator, I can manage product/category details, prices, and stock.

## Core business rules

- Only active products can be added to a cart or ordered.
- Quantities must be positive integers; the server validates them even if a client also validates them.
- A cart is owned by one customer. Cart totals are estimates; the server recalculates prices and totals at checkout.
- An order stores price and product-name snapshots so later catalog edits do not rewrite purchase history.
- Stock must never become negative. Checking and decrementing stock must be protected against concurrent checkouts.
- An order cannot be reported as paid unless the simulated payment succeeds.
- A failed payment must not leave stock permanently unavailable. The order and stock changes must follow a defined rollback or release path.
- Customers can only read or change their own cart, orders, and delivery information; administrators manage catalog and stock.
- Payment is simulated only. No card, bank, or other real payment credentials are accepted or stored.

## Initial architecture

Use a modular monolith: one deployable Spring Boot application with business responsibilities separated by feature. This keeps local transactions and debugging straightforward while avoiding premature network calls and independent service operations. Package-by-feature is preferred over package-by-layer so each module's controller, service, persistence, and DTO concerns remain close together.

```text
HTTP request
  → module controller (HTTP validation and response)
  → module service (business rules and transaction boundary)
  → repository (persistence)
  → MySQL
```

Initial modules: `auth`, `user`, `catalog`, `inventory`, `cart`, `order`, `payment`, and `delivery`, plus shared `config` and `common` only for genuinely cross-cutting concerns. Controllers expose DTOs; persistence entities are not the public API contract.

Orders coordinate checkout across cart, inventory, and payment behavior through application services. Keep the first version within one process and use local database transactions where appropriate. We will explicitly design stock concurrency and payment-failure recovery in those feature milestones before implementing them.

## Initial conceptual data model

```text
User 1 ── 1 Cart 1 ── * CartItem * ── 1 Product * ── 1 Category
User 1 ── * Order 1 ── * OrderItem
Order 1 ── 0..1 Payment
Order 1 ── 0..1 Delivery
Product 1 ── 1 Inventory (single-store MVP)
```

Likely records and responsibilities:

- `User`: identity, password hash, and role.
- `Category`: category name and active state.
- `Product`: category, name, description, current price, and active state.
- `Inventory`: product stock counts; later can gain a store relationship.
- `Cart` / `CartItem`: customer's current selections and quantities.
- `Order` / `OrderItem`: order lifecycle and immutable purchase snapshots.
- `Payment`: simulated outcome and status associated with an order.
- `Delivery`: basic assignment/status and simulated ETA associated with an order.

This is a conceptual model, not a finalized migration. Keys, uniqueness constraints, monetary precision, status values, and reservation fields will be decided with the related implementation milestones.

## Request and data flow examples

### Browse catalog

`GET` request → catalog controller → catalog service → product repository → response DTO. Inactive products are excluded from customer browsing; later milestones will specify search and pagination contracts.

### Checkout (target behavior)

Customer request → order controller → order service validates cart and current catalog state → inventory logic safely reserves/decrements stock → order items capture purchase-time names and prices → simulated payment records success/failure → order response. The exact transaction and compensation boundaries are acceptance criteria for the order/payment milestones, not assumed to be solved by a simple transaction annotation.

## Non-functional goals for the first release

- Clear validation and consistent API errors.
- No secrets committed to source control.
- Automated tests for business rules and persistence behavior.
- Schema changes tracked as migrations before production-readiness work.
- Local development can be reproduced from documented instructions.
- Logs and errors must not expose passwords, tokens, or payment credentials.

## Deferred decisions and out of scope

- Multiple stores, delivery zones, and store-specific pricing.
- Real payment-provider integration.
- Redis, Kafka, microservices, Kubernetes, and cloud deployment.
- Live courier integrations and real-time location tracking.
- Promotions, loyalty points, refunds, and advanced fulfillment optimization.

These are deferred because they add infrastructure or business rules before the single-store core flow is validated.

## Milestone acceptance criteria

This design milestone is complete when:

1. The MVP actors, core user journeys, and business rules are documented.
2. The single-store assumption and major deferred features are explicit.
3. The initial module boundaries, request flow, and conceptual data model are reviewable.
4. High-risk questions—especially stock concurrency, purchase-time prices, and payment failure—are called out for the milestones that will resolve them.
5. No application behavior is claimed as implemented or verified.

## Roadmap and acceptance checkpoints

| Stage | Main work | Acceptance checkpoint |
| --- | --- | --- |
| 1. Requirements and design | Scope, rules, modules, conceptual schema | This document reviewed and agreed |
| 2. Foundation | Compatible Spring Boot setup, configuration, health check, README | App starts and health endpoint responds |
| 3. Catalog | Category/product APIs, validation, search, paging | API contract and negative cases verified |
| 4. Authentication | Registration, password hashing, tokens, role checks | Authenticated and unauthorized cases verified |
| 5. Inventory | Stock model and reservation concurrency control | Concurrent requests cannot oversell |
| 6. Cart | Cart ownership, item operations, calculated estimate | Ownership and quantity edge cases verified |
| 7. Orders and payment | Price snapshots, lifecycle, simulated success/failure | Success/failure leaves consistent order and stock state |
| 8. Delivery and notifications | Basic delivery lifecycle and notification design | Order status progression verified |
| 9. Production readiness | Tests, migrations, security, logs, metrics | Core paths pass defined checks; local setup documented |
| 10. Docker and monolith CI/CD | Container setup and automated build/test pipeline | Reproducible local container run and passing CI |
| 11. Selective extraction | Extract only justified business boundaries | Ownership, contracts, operations, and failure modes documented |
| 12. Distributed systems | Gateway, REST/events, Kafka and reliability patterns as justified | Duplicate, retry, timeout, and partial-failure behavior verified |
| 13. Cloud and hardening | Deployment, backups, monitoring, recovery, cost controls | Deployment and recovery procedures exercised |

## Required versus advanced

**Required core:** catalog, customer authentication/authorization, inventory safety, cart, orders, simulated payment, basic delivery status, tests, migrations, local run documentation, Docker, and monolith CI.

**Advanced/later:** multi-store operations, Redis, Kafka, service extraction, gateway, cloud hosting, Terraform, Kubernetes, distributed tracing, and independent microservice delivery pipelines. Each requires a demonstrated need and a separate design review before introduction.
