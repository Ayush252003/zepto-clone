# Cart

Each customer has at most one cart, created on the first add. A cart stores product IDs and quantities, not prices or names. Responses resolve current product details and prices, so the subtotal is an estimate rather than a checkout promise.

Only active products in active categories can be added or have their quantity changed. If a product later becomes inactive, its line remains visible and can still be removed. Checkout rejects inactive items and rechecks current prices and stock. Stock is reserved only during checkout.

## Routes

All routes require a CUSTOMER token. The owner is taken from the JWT, never from a request parameter.

| Method and path | Behavior |
| --- | --- |
| `GET /api/v1/cart` | Read the cart; an account without one gets an empty response |
| `POST /api/v1/cart/items` | Add to a product line, incrementing it if present |
| `PUT /api/v1/cart/items/{productId}` | Replace the line quantity |
| `DELETE /api/v1/cart/items/{productId}` | Remove the line |

POST and PUT accept a positive integer `quantity`. A response includes current product details, unit price, line total, and informational `availableNow` stock. That stock may change before checkout.

## Concurrent edits

Cart writes lock the owning user row before finding or creating the cart. This serializes writes for one customer, including simultaneous first adds when no cart row exists. Other customers can write independently. Unique constraints on `carts.user_id` and `(cart_id, product_id)` provide a second integrity check. Reads do not take the write lock.

Flyway `V4` creates the cart tables. The MySQL integration suite covers ownership, quantity updates, current pricing, and concurrent increments; it is opt-in and targets `zepto_clone_test`.
