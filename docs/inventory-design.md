# Inventory and reservations

Inventory is separate from the product catalog. Each product has `on_hand` and `reserved` counts; available stock is derived as `on_hand - reserved`. New products begin with zero stock. Cart changes do not reserve units.

## Preventing oversell

Reservation uses a conditional MySQL update: increase `reserved` only when `on_hand - reserved` is at least the requested quantity. The database evaluates the check and update as one write, so two checkouts cannot both claim the same last unit. A reservation containing several products runs in one transaction and updates product rows in ID order; if one item is unavailable, the earlier updates roll back.

Reservations have a caller-supplied UUID and move from `RESERVED` to `COMMITTED` or `RELEASED`. Commit lowers both `on_hand` and `reserved`; release lowers only `reserved`. Repeating the same terminal operation does not adjust stock twice. An opposite terminal operation or reusing an ID with different contents is rejected.

## Admin API

Both routes require an ADMIN token:

| Method and path | Behavior |
| --- | --- |
| `GET /api/v1/admin/products/{productId}/inventory` | Return on-hand, reserved, and available counts |
| `PUT /api/v1/admin/products/{productId}/inventory` | Set physical on-hand count; cannot set it below reserved stock |

Reservation methods are internal service operations used by checkout; there is no public endpoint to reserve inventory directly. The database constraints backstop non-negative counts and `reserved <= on_hand`.

Flyway `V3` creates the inventory and reservation tables and gives existing products zero stock. MySQL integration tests are opt-in via `INVENTORY_MYSQL_TESTS=true` and use only `zepto_clone_test`.
