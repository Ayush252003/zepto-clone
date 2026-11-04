# Delivery and notifications

A delivery is created after simulated payment succeeds. It starts in `PREPARING`; an administrator advances it through `ASSIGNED`, `PACKED`, `OUT_FOR_DELIVERY`, and `DELIVERED`, one step at a time. Assignment can record a courier display name and a simulated ETA. There is no courier dispatch or location tracking.

Customers can read delivery progress for their own orders and list or mark their own in-app notifications as read. Admins update delivery status. The API checks ownership from the authenticated customer, not a caller-supplied user ID.

Order, payment, and delivery notifications are stored in MySQL in the same transaction as the state change that produced them. This keeps the in-app record consistent with the order lifecycle. `V6` creates the delivery and notification tables; no email/SMS provider or message broker is used.

If notifications later need to leave the process, move delivery to an outbox-backed flow so database updates and message publication can recover from failures independently.
