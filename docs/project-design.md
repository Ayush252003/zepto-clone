# Product scope and domain model

## Release scope

This project models a quick-commerce service for one store/service area. Customers browse products, keep a cart, check out, use a simulated payment, and follow basic delivery status. Administrators maintain categories, products, and stock. There is no real payment processing, courier dispatch, or customer-facing store selection.

The single-store assumption keeps inventory ownership simple. Store IDs and service-area rules belong in the model only when the product needs more than one store.

## Roles and journeys

| Role | Main actions |
| --- | --- |
| Visitor | Browse active products and categories; register or sign in |
| CUSTOMER | Manage a cart, place and cancel eligible orders, view order/delivery status, read notifications |
| ADMIN | Manage catalog and stock; advance delivery status |

Registration always creates a CUSTOMER. An administrator is provisioned through the startup bootstrap settings. The API derives customer ownership from the JWT; clients do not choose a user ID for carts or orders.

## Business rules

- Only active products in active categories can be newly added to a cart or checked out.
- Cart totals use current prices and are estimates. Checkout reads current catalog data again.
- An order keeps its item name, SKU, and price as they were at purchase time.
- Stock availability is `on_hand - reserved`. Reservations are created at checkout, never when a cart changes.
- Payment is simulated. Success commits reserved stock; failure, timeout, or eligible cancellation releases it.
- Customers can access only their own cart, orders, delivery details, and notifications.
- Product/category deletion is soft deactivation so existing references and order history remain intact.

## Modules and data

The code is organized by business feature: `auth`, `user`, `catalog`, `inventory`, `cart`, `order`, `delivery`, and `notification`. Cross-cutting configuration and API error handling live in `config` and `common`. Payment simulation is part of the order module rather than a separate service.

```text
User 1 ── 0..1 Cart 1 ── * CartItem * ── 1 Product * ── 1 Category
User 1 ── * Order 1 ── * OrderItem
Order 1 ── 0..1 Payment
Order 1 ── 0..1 Delivery
Product 1 ── 1 Inventory
Reservation 1 ── * ReservationItem
```

Flyway owns schema changes; Hibernate validates mappings at startup. The database enforces unique identifiers and relationships in addition to service-level checks. Order and payment writes use idempotency keys so retries do not create duplicate work.

## Deliberately deferred

Multi-store operations, real payment/courier providers, email/SMS, promotions, refunds, Kafka, microservice extraction, Kubernetes, and cloud hosting are outside the current scope. Adding them would change transaction, ownership, or operational requirements and needs a separate design decision.
