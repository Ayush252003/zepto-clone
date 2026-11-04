# Delivery tracking and notifications

Delivery is created only after simulated payment succeeds. The single-store MVP starts at `PREPARING`; an ADMIN then advances exactly one step at a time through `ASSIGNED`, `PACKED`, `OUT_FOR_DELIVERY`, and `DELIVERED`. Assignment can include a courier display name and an updated ETA. The ETA is simulated and is not a courier or map integration.

Customer delivery reads and in-app notifications are scoped to the authenticated customer. Each order, payment, or delivery transition writes its notification in the same MySQL transaction as the state change. That keeps the customer-visible timeline consistent if a database operation fails. It is synchronous because this is an in-process modular monolith; when notifications must leave the process, a transactional outbox and a message broker can be designed around measured reliability needs.

Flyway V6 creates deliveries and notifications. The API supports customer delivery lookup, customer notification list/read, and ADMIN delivery status updates. No email/SMS provider or Kafka dependency is used.
