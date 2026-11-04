# Catalog

The catalog owns product details, category membership, current price, and active state. Inventory is a separate module so product edits do not bypass stock rules. HTTP responses use DTOs rather than JPA entities.

## Rules

- Products have a unique SKU, one category, a positive INR price, and an active flag. Categories have unique names and slugs.
- Public reads show only active products in active categories. Admin reads can include inactive records.
- Delete operations deactivate records. A category cannot be deactivated while it contains active products.
- Reactivating a product requires its category to be active.
- MySQL uniqueness constraints are the final guard when simultaneous requests pass application-level checks.

## Routes

All paths are under `/api/v1`.

| Method and path | Access | Notes |
| --- | --- | --- |
| `GET /categories` | Public | Active categories |
| `GET /products` | Public | Active products; supports `q`, `categoryId`, `page`, `size`, and `sort` |
| `GET /products/{id}` | Public | Active product only |
| `GET /admin/categories` | ADMIN | Includes inactive categories |
| `POST`, `PUT /admin/categories[/{id}]` | ADMIN | Create or replace category name/slug |
| `DELETE /admin/categories/{id}` | ADMIN | Deactivate; conflicts while active products remain |
| `PATCH /admin/categories/{id}/active` | ADMIN | Change active state |
| `GET /admin/products` | ADMIN | Same filters as public listing, includes inactive products |
| `GET /admin/products/{id}` | ADMIN | Includes inactive product |
| `POST`, `PUT /admin/products[/{id}]` | ADMIN | Create or replace product fields |
| `DELETE /admin/products/{id}` | ADMIN | Deactivate |
| `PATCH /admin/products/{id}/active` | ADMIN | Change active state |

Product listing uses a zero-based page (default `0`), page size `20` (maximum `50`), and a default sort of `name,asc`. Sort fields are limited to `name`, `price`, and `createdAt`; `id` is appended as a stable tie-breaker. Search matches product name or SKU without regard to case and is limited to 100 characters. Invalid query values return `400`; missing records return `404`; duplicate identifiers and conflicting category changes return `409`.

## Persistence and search

Flyway `V1` creates categories and products, their relationship and uniqueness constraints, and a positive-price check. Hibernate validates the schema instead of updating it. The API returns price as a decimal and identifies currency as INR.

Search uses a SQL substring match. That is adequate for the current learning project, but a leading wildcard can become expensive as the catalog grows. Measure real query behavior before adding full-text search or an external search service.
