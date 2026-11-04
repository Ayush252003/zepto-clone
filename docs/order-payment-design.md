# Orders and simulated payment

Checkout turns an editable cart estimate into a durable purchase. The order stores item name, SKU, quantity, and unit-price snapshots so later catalog changes cannot rewrite purchase history. A customer idempotency key prevents a retried checkout request from creating another order.

The flow is deliberately synchronous and stays in the modular monolith:

```text
POST /orders (customer + idempotency key)
  → lock customer row (serializes against cart writes)
  → validate current cart and prices
  → reserve all stock with the existing atomic inventory service
  → save order + immutable line snapshots in the same MySQL transaction
  → PENDING_PAYMENT

POST /orders/{id}/payment (simulation + idempotency key)
  → SUCCESS: commit reservation and confirm order
  → FAILURE or TIMEOUT: release reservation and cancel order
```

No card details or payment provider are accepted. The outcome request exists only to exercise simulated success, failure, and timeout paths. A pending order may also be cancelled, which releases stock. A paid order cannot be cancelled in this first version because refunds are not part of the agreed scope.

Flyway V5 creates `orders`, `order_items`, and `payments`, with database constraints backing status, idempotency, and relationships. Customer routes expose checkout, order history, details, cancellation, and simulated payment. Tests use only `zepto_clone_test`.

The local MySQL transaction makes order creation and reservation atomic. If an external payment provider is introduced later, network calls must not be made while holding this transaction open; that change would need an outbox/saga design and explicit retry/compensation rules.
