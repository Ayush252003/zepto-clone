# Inventory Design (Proposed)

## Problem

Suppose there is one unit of a product and two customers check out at nearly the same time. If each request first reads “1 available” and later writes a reservation, both can succeed and the store promises the same unit twice. Inventory needs a single database-controlled decision that cannot oversell, plus a way to return stock when checkout fails.

The cart milestone comes later. A cart is only an estimate and can sit unused, so adding an item to a cart must not reserve stock. Reservation belongs at checkout; this milestone builds and verifies that capability for the future order module without exposing a premature customer reservation endpoint.

## Options and recommendation

| Strategy | How it works | Benefits | Costs and fit here |
| --- | --- | --- | --- |
| Pessimistic row lock | Read a stock row with `SELECT ... FOR UPDATE`, check availability, then update it | Straightforward to reason about; competing updates queue | Holds locks across service logic and needs a consistent row order for multi-product reservations. Works, but is more locking than this single-row operation needs. |
| Optimistic version | Read a versioned row and update only if the version is unchanged; retry conflicts | Good when conflicts are rare and reads are frequent | Requires retries and careful whole-transaction handling during contention. Hot products can cause repeated retries. |
| Atomic conditional update (recommended) | One SQL update increments reserved quantity only when `on_hand - reserved >= requested` | The database makes the check and change indivisible; no application-level read/write gap | Requires a small explicit repository update and affected-row handling. Multi-product reservations still need one transaction and a stable product-id lock order. |

Use the atomic conditional update for this MySQL single-store service. InnoDB serializes updates to the same row; a competing request re-evaluates the condition after the first update. Only requests whose conditional update changes one row succeeded. For a reservation containing several products, update product rows in ascending ID order and keep all work in one transaction so an unavailable item rolls back earlier updates.

## Model and lifecycle

```text
Product 1 ── 1 Inventory
Inventory 1 ── * ReservationItem * ── 1 Reservation

on_hand: total physical units
reserved: units held by active reservations
available: on_hand - reserved (derived; never stored separately)

RESERVED ── commit ──> COMMITTED
    └────── release ──> RELEASED
```

- `inventory` has one row per product, integer `on_hand` and `reserved` counts, non-negative checks, and `reserved <= on_hand`. Existing products are backfilled with zero stock; new products get a zero-stock row in the same transaction that creates the product.
- `inventory_reservations` stores a caller-supplied UUID and lifecycle status. `inventory_reservation_items` stores the product and quantity snapshot for each reservation.
- Reserve increments `reserved`, not `on_hand`, so available stock falls while physical stock stays visible. Commit decrements both counters. Release decrements only `reserved`.
- Commit and release are transactionally idempotent for the same reservation ID: repeating the same terminal operation has no further stock effect. Committing a released reservation or releasing a committed reservation is a conflict.
- A repeated reserve ID must have the same items and still be RESERVED; a different payload or terminal reservation is a conflict. This gives the future order flow a stable retry key without introducing a separate distributed idempotency system.
- Admin stock updates set the physical `on_hand` total. They fail if the new total would be below already reserved stock. The database constraint remains the final guard.

## API and access

| Method and path | Access | Behavior |
| --- | --- | --- |
| `GET /api/v1/admin/products/{productId}/inventory` | ADMIN | Returns `productId`, `onHand`, `reserved`, and derived `available` |
| `PUT /api/v1/admin/products/{productId}/inventory` | ADMIN | Body `{ "onHand": 25 }`; sets physical total, returns the resulting counts |

The reservation, commit, and release operations are application-service methods, not public HTTP routes yet. The order milestone will call them as part of checkout. Public product/cart APIs will not promise availability as a guarantee; it can change before checkout.

## Request and transaction flow

```text
ADMIN PUT stock
  → InventoryController validates DTO and ADMIN token
  → InventoryService updates on_hand if it does not undercut reserved
  → InventoryRepository / MySQL constraint
  → response contains onHand, reserved, available

Future order service calls reserve(id, items)
  → sort product IDs
  → transaction inserts reservation and item snapshots
  → conditional inventory updates per product
  → any unavailable item throws; the entire transaction rolls back
```

`InventoryService` owns stock rules and reservation transactions. `ProductService` initializes inventory when it creates a product. The future order service will own the larger checkout transaction that combines cart validation, inventory reservation/commit or release, price snapshots, and simulated payment.

## Files and compatibility impact

- Add `inventory` entity, repository, DTOs, controller, service, and domain exceptions.
- Add Flyway `V3` for inventory and reservation tables, constraints, and zero-stock backfill.
- Update `ProductService` so new products receive an inventory row transactionally; adapt its unit tests.
- Update README and project progress with stock setup and checks.
- Add no new library or infrastructure dependency. Existing `ADMIN` route rules already protect the proposed HTTP endpoints.
- Product/category endpoints keep their current response shapes; the new inventory routes are additive.

## Acceptance criteria

1. V3 applies to the separate test database and the current local database; Hibernate validates the mappings.
2. Existing and newly created products have exactly one inventory row with zero stock initially.
3. ADMIN can read/set stock; missing product returns `404`; negative quantity returns `400`; setting `onHand` below `reserved` returns `409`.
4. Anonymous access returns `401`; a CUSTOMER token returns `403` for both inventory routes.
5. Reservations reject zero/negative quantities and unavailable or missing inventory without changing stock.
6. A multi-product reservation that fails on one product leaves every product's stock/reserved counts unchanged.
7. Release and commit transition counts exactly once; duplicate terminal calls do not double-adjust; opposite terminal actions conflict.
8. A MySQL-backed simultaneous reservation test starting with N units permits at most N units to be reserved and never leaves `available` negative.
9. Existing catalog and authentication tests continue to pass.

## Verification setup

Docker is not installed, so the concurrency checks should use a dedicated local MySQL test database, `zepto_clone_test`, with the same Flyway migrations. Do not clean or truncate the developer database as a test setup. Tests should create and clean their own test rows or reset only their isolated schema.

## Implementation and verification record

The approved design is implemented in the inventory module. Flyway `V3` creates and backfills stock/reservation tables; `ProductService` initializes stock for new products; the ADMIN inventory endpoints and internal reservation lifecycle are in place.

The MySQL integration test is opt-in with `INVENTORY_MYSQL_TESTS=true` and hardcodes its datasource to `zepto_clone_test`. It verifies concurrent reservation limits, multi-product rollback, stock undercut rejection, and idempotent release/commit behavior. The isolated database must be created locally before running the test class.
