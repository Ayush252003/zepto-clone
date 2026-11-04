# Orders and payment simulation

Checkout turns the current cart into an order. It locks the customer row while reading the cart, rechecks product status and current prices, reserves all required stock, and saves item name/SKU/price snapshots. An `Idempotency-Key` makes a retry for the same customer return the existing order instead of creating a duplicate.

```text
POST /api/v1/orders
  validate cart → reserve stock → save PENDING_PAYMENT order

POST /api/v1/orders/{id}/payment
  SUCCESS        → commit reservation, confirm order
  FAILURE/TIMEOUT → release reservation, cancel order
```

Payment is a simulation. The request chooses an outcome to exercise the order lifecycle; the API never accepts card or bank details. A pending order can be cancelled and its stock released. Refunds and cancellation of paid orders are not implemented.

Order creation, inventory reservation, and persistence share a MySQL transaction. `V5`–`V8` add the order, payment, and related schema; `V7` scopes payment idempotency to an order, and `V8` records the cart-line relationship used for cleanup. MySQL integration tests cover retry behavior, payment outcomes, stock, and customer ownership against `zepto_clone_test`.

If a real payment provider is added, do not make its network call while holding the checkout transaction open. Provider retries and compensation need a separate design, likely with an outbox and explicit state transitions.
