# Cart Design and Implementation

## Problem

A customer adds milk to a cart, then gets interrupted for an hour. If the cart held stock during that hour, the last bottle could remain unavailable even though the customer may never order it. A cart should remember intent and show an estimate; the inventory reservation created in the previous milestone should happen only during checkout.

This feature needs to keep each customer's items private, handle two browser tabs changing the same cart safely, and calculate prices from the current catalog without pretending the displayed total is guaranteed at checkout.

## Scope and rules

- Each CUSTOMER has at most one current cart. The cart is created lazily when the customer first adds an item; reading a customer with no cart returns an empty cart without writing to the database.
- Only active products in active categories can be added or have their quantities changed.
- Adding an existing product increases its quantity. Setting quantity replaces the current quantity. Removing an item is allowed even if the product has since become inactive.
- Cart rows store product IDs and quantities, not prices or names. Every response reads current product details and price, so a price edit changes the estimate immediately.
- Cart display includes each product's active state and current available stock. The cart does not reserve stock and does not guarantee that quantity remains available. Checkout rechecks active state, price, and stock, then creates the inventory reservation.
- Existing items are retained if a product becomes inactive; the response marks them inactive and checkout will reject the cart until the customer removes or replaces them.
- The server calculates each line total and cart subtotal using current catalog prices. Orders will save immutable purchase-time price/name snapshots in the later order milestone.

## API contract

All routes require a valid Bearer token with the `CUSTOMER` role.

| Method and path | Behavior | Success |
| --- | --- | --- |
| `GET /api/v1/cart` | Read current cart or empty cart | `200` cart and estimate |
| `POST /api/v1/cart/items` | Add quantity to an existing product line | `200` updated cart |
| `PUT /api/v1/cart/items/{productId}` | Set the exact quantity for a line | `200` updated cart |
| `DELETE /api/v1/cart/items/{productId}` | Remove a line; removing a missing line is harmless | `204` |

POST and PUT body: `{ "quantity": 2 }`. Quantities must be positive integers. Adding a product that is missing or inactive returns `404` or `409`, respectively. A missing cart line on PUT returns `404`. Invalid quantity returns `400`. Anonymous requests return `401`; ADMIN tokens receive `403`.

Cart response shape:

```json
{
  "items": [
    {
      "productId": 42,
      "sku": "MILK-001",
      "name": "Milk",
      "quantity": 2,
      "unitPrice": 32.50,
      "lineTotal": 65.00,
      "active": true,
      "availableNow": 7
    }
  ],
  "subtotal": 65.00,
  "currency": "INR"
}
```

`availableNow` is informational only. It can change immediately after the response; checkout is the authoritative stock decision.

## Data model

```text
User 1 ── 0..1 Cart 1 ── * CartItem * ── 1 Product
```

- `carts`: generated ID, unique `user_id` foreign key, creation/update timestamps.
- `cart_items`: generated ID, `cart_id`, `product_id`, positive integer `quantity`, unique `(cart_id, product_id)` and foreign keys to cart/product.
- No price or product-name columns are stored in a cart item. The order module will store those snapshots at checkout.
- Migration `V4` creates the two tables. No external dependency is needed.

## Concurrent cart updates

Two browser tabs for the same customer might add the same product at once. Both could read quantity 1 and each write quantity 2, losing one add. A unique constraint prevents duplicate lines but alone does not prevent a lost update.

| Strategy | How it works | Trade-off here |
| --- | --- | --- |
| Optimistic version | Compare a cart version on save and retry when stale | Good when collisions are rare, but retries complicate the user's “add one more” operation. |
| Atomic upsert | Let MySQL insert a new line or increment its quantity in one statement | Compact, but vendor-specific and more awkward to combine with active-product validation and replacement semantics. |
| Pessimistic owner-row lock (recommended) | Lock the existing customer row while an item mutation creates/finds the cart and reads/writes its items | Makes one customer's edits serial, including first-cart creation. No lock is needed on a missing cart row, which avoids gap-lock races. Different customers remain independent. |

Add/update/remove operations select the authenticated customer row `FOR UPDATE` before looking up or creating the cart. Since that user row always exists and all mutations take this lock first, two first-time writes for the same customer cannot race to create duplicate carts. The unique constraint on `carts.user_id` and on `(cart_id, product_id)` remains a final integrity guard. Reads do not acquire a write lock.

## Request flow and module boundaries

```text
HTTP + JWT customer
  → CartController (validate quantity, derive customer email from JWT)
  → CartService (resolve owner, lock cart, enforce active-product rules)
  → Cart / CartItem repositories + catalog/inventory reads
  → CartResponse with current product/price/stock estimate
```

`CartService` owns cart rules and transaction boundaries. The owner comes from the authenticated JWT subject; clients never send a user ID. A write locks that customer's row first, which serializes same-customer changes and safely handles a cart that has not been created yet. Product and inventory repositories supply current catalog/stock facts. Cart code must not call inventory reservation operations.

## Files and compatibility impact

- Add `cart` entities, repositories, DTOs, service, and controller.
- Add Flyway `V4` for cart and cart-item tables.
- Update `SecurityConfiguration` to allow `/api/v1/cart` only to CUSTOMER tokens.
- Add tests for owner isolation, price refresh, inactive products, quantity rules, same-user concurrent adds, and route authorization.
- No existing catalog/auth response shape changes and no new library or infrastructure dependency.
- Update README and project progress with route examples and verification steps.

## Acceptance criteria

1. V4 applies to both the isolated MySQL test schema and the development schema; Hibernate validates the mappings.
2. A customer with no persisted cart gets an empty response; the first add creates exactly one cart.
3. A customer can add, increment, replace quantity, and remove lines. Concurrent increments from separate transactions are not lost and there is one row per product per cart.
4. A customer cannot read or modify another customer's cart; owner identity comes only from the JWT.
5. Invalid quantities return `400`; missing products/cart lines return `404`; inactive products cannot be newly added or quantity-edited; removal still works.
6. Responses use current product names/prices and current stock for estimates; a catalog price update changes the estimate without rewriting the cart row.
7. Cart mutations do not change inventory `reserved`; checkout remains the only reservation point.
8. Anonymous access returns `401`; ADMIN access to customer cart routes returns `403`.
9. Existing catalog, authentication, and inventory tests continue to pass.

## Verification setup

Use the existing isolated `zepto_clone_test` MySQL database for integration and concurrency tests. Tests must create and remove only their own generated rows and must not clear the development database. Docker remains unnecessary for this milestone.

## Implementation status

The approved design is implemented. Flyway V4 creates both tables; `CartController` exposes the four routes above. The MySQL integration tests exercise same-customer concurrent adds and run only against `zepto_clone_test`. Development database and live HTTP verification are recorded in `PROJECT_PROGRESS.md`.
