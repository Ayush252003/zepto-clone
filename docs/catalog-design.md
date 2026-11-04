# Catalog Module: API and Persistence Design

## Goal and boundaries

The catalog owns product descriptions, category membership, current price, and whether an item is active. Inventory owns stock counts. Keeping stock out of `Product` prevents catalog edits from becoming the place where checkout concurrency rules are implemented.

The first implementation will remain inside the Spring Boot modular monolith:

```text
HTTP request → catalog controller → catalog service → JPA repository → MySQL
```

Controllers validate the HTTP contract and return DTOs. Services apply rules and coordinate persistence. Repositories read/write entities. Product/category entities will not be returned directly from the API.

## Catalog rules

- Each product has a unique SKU, name, category, description, positive INR price with two fractional digits, and active flag.
- A product belongs to one existing category. Names need not be unique; SKU is the stable unique identifier.
- A category has a unique name and slug, plus an active flag.
- Customer catalog reads include only active products in active categories. Inactive records remain addressable to administrators.
- Product removal is a soft deactivation. This preserves references from carts and historical orders; checkout will reject an inactive product.
- A category cannot be deactivated while it has active products. The API returns a conflict; the administrator must first deactivate or move those products.
- The catalog does not own stock, reservations, carts, or order price snapshots.
- Authentication and role enforcement are a later milestone. The `/admin` route namespace communicates intended access, but is not secure until Spring Security is implemented.

## API contract proposal

Base path: `/api/v1`.

| Method and path | Intended access | Behavior | Success |
| --- | --- | --- | --- |
| `GET /categories` | Public | List active categories | `200` with category DTO array |
| `GET /admin/categories` | Admin | List all categories, including inactive ones | `200` with category DTO array |
| `GET /products` | Public | List active products; optional `q`, `categoryId`, `page`, `size`, and `sort` | `200` with page metadata and product DTOs |
| `GET /products/{id}` | Public | Fetch one active product | `200`; `404` if missing or inactive |
| `GET /admin/products` | Admin | List all products, including inactive ones, with the same filters and paging | `200` with page metadata and product DTOs |
| `GET /admin/products/{id}` | Admin | Fetch an active or inactive product | `200`; `404` if missing |
| `POST /admin/categories` | Admin | Create a category | `201` and category DTO |
| `PUT /admin/categories/{id}` | Admin | Replace category name/slug | `200`; `404` if missing; `409` for duplicate slug/name |
| `DELETE /admin/categories/{id}` | Admin | Deactivate an unused category | `204`; `409` while active products reference it |
| `PATCH /admin/categories/{id}/active` | Admin | Set category active state (`{"active": true}` or `false`) | `204`; `409` if deactivation conflicts |
| `POST /admin/products` | Admin | Create a product | `201` and product DTO |
| `PUT /admin/products/{id}` | Admin | Replace editable product fields | `200`; `404` if missing; `409` for duplicate SKU |
| `DELETE /admin/products/{id}` | Admin | Deactivate product (soft delete) | `204`; `404` if missing |
| `PATCH /admin/products/{id}/active` | Admin | Set product active state (`{"active": true}` or `false`) | `204`; `409` if activating under inactive category |

Example product creation request:

```json
{
  "sku": "FRUIT-BAN-001",
  "name": "Banana",
  "description": "Fresh bananas, sold by the dozen",
  "categoryId": 12,
  "price": 49.00
}
```

Example product response:

```json
{
  "id": 42,
  "sku": "FRUIT-BAN-001",
  "name": "Banana",
  "description": "Fresh bananas, sold by the dozen",
  "category": { "id": 12, "name": "Fruits" },
  "price": 49.00,
  "currency": "INR",
  "active": true
}
```

The response deliberately gives a category summary instead of exposing the entity relationship. Prices use decimal arithmetic in the database and Java; floating-point types are not appropriate for money.

## Search, paging, and sorting

- `q` is an optional case-insensitive substring search over product name and SKU. Empty/blank `q` behaves as absent; limit it to 100 characters.
- `categoryId` is an optional exact filter.
- `page` is zero-based and defaults to `0`.
- `size` defaults to `20`, must be positive, and is capped at `50`.
- `sort` accepts a small allowlist: `name`, `price`, or `createdAt`, with `asc` or `desc`. Invalid sort fields/directions return `400`.
- Add `id` as a deterministic tie-breaker so adjacent pages do not randomly reorder equal values.
- A basic SQL `LIKE` query is adequate for the initial catalog. It can scan many rows, especially with a leading wildcard; measure before introducing database full-text search or a dedicated search engine.

Example: `GET /api/v1/products?q=ban&categoryId=12&page=0&size=20&sort=price,asc`.

## Persistence model proposal

`Category` table:

- `id` primary key
- `name` required, bounded length
- `slug` required and unique
- `active` required
- audit timestamps

`Product` table:

- `id` primary key
- `sku` required and unique
- `name` required, bounded length
- `description` optional, bounded length
- `category_id` required foreign key to category
- `price` required decimal with two fractional digits and a positive-value constraint
- `active` required
- audit timestamps

Use a foreign key and a database unique constraint for SKU/slug, in addition to friendly application validation. Application checks alone cannot prevent two simultaneous requests from creating the same identifier. Add indexes for active/category filters and any proven query need. Do not cascade category deletion into products.

Schema creation strategy is still a foundation decision for the first database-backed feature. Preferred production direction is versioned migrations; using Hibernate `ddl-auto=update` as a long-term schema owner is not planned. Before implementing persistence, choose whether to bring Flyway forward into this milestone or do a disposable local schema first, then migrate before production readiness.

## Validation and error behavior

- Reject blank or overlong SKU, name, and category name/slug.
- Reject missing category IDs, non-positive prices, and prices with more than two fractional digits.
- Reject invalid paging and sort values with `400 Bad Request`.
- Return `404 Not Found` for missing records and `409 Conflict` for unique-constraint collisions or a category that still has active products.
- Use the centralized error response planned for the application; do not return stack traces or database messages to clients.

## Tests and acceptance criteria

The implementation is accepted when:

1. Valid categories and products can be created and read through the API.
2. Updating, deactivating, listing, and reactivating products/categories follows the rules above; historical references are not physically deleted.
3. Public listing excludes inactive products and products in inactive categories.
4. Search, category filtering, paging boundaries, and allowed sorting return predictable results.
5. Invalid request fields, missing categories, duplicate SKU/slug, and category deactivation conflicts return the documented statuses.
6. Two concurrent creates with the same SKU/slug cannot both persist successfully.
7. API responses contain DTOs and never leak persistence implementation details.
8. Tests use an isolated database and do not depend on a developer's existing MySQL data.

## Decisions applied for implementation

- Use Flyway from the first database-backed change; Hibernate validates the schema and does not own schema updates.
- Do not add a category detail endpoint to the MVP.
- Enforce category name and slug uniqueness case-insensitively in application checks and database collation/constraints.
- Expose the route contracts through generated OpenAPI documentation. Authentication must protect administrator routes before deployment beyond local development.
