# Authentication and Authorization Design

## Scope

This milestone adds customer registration, password-based login, short-lived JWT access tokens, a current-user endpoint, and authorization rules for existing catalog routes. It does not add refresh tokens, password reset, OAuth login, or login throttling.

## Authentication versus authorization

- **Authentication** answers “who is making this request?” Registration creates an account; login verifies a password and issues a signed token.
- **Authorization** answers “what may this account do?” The token carries a role, and Spring Security checks that role against endpoint rules.

## Token choice and trade-offs

Use a stateless 15-minute Bearer JWT signed with HS256 and a Base64-encoded 256-bit-or-longer key from `JWT_SECRET_BASE64`. This fits the current single service and HTTP API without storing server sessions. The signing secret must remain private; every component that can validate this HMAC token can also mint one. If services split later, reconsider asymmetric signing and key distribution.

There is no refresh-token flow or immediate token revocation in this milestone. A role change or account change may take up to 15 minutes to affect an already issued token. Login throttling and refresh-token lifecycle are deferred.

Passwords are hashed with BCrypt and never returned. Registration always creates a `CUSTOMER`; clients cannot choose a role. An initial admin is provisioned only when both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` are supplied. Existing accounts are not silently promoted or overwritten.

## Request flow

```text
POST /api/v1/auth/login
  → AuthController → AuthService → UserRepository + BCrypt
  → JWT issuer → signed access token

Request with Authorization: Bearer <token>
  → Spring Security JWT decoder verifies signature and expiry
  → roles claim becomes ROLE_CUSTOMER / ROLE_ADMIN authorities
  → URL authorization rule decides whether the handler may run
```

## Endpoint and access contract

| Method and path | Access | Result |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Public | `201` user summary; new users are always `CUSTOMER` |
| `POST /api/v1/auth/login` | Public | `200` Bearer token and expiry timestamp; invalid credentials return `401` |
| `GET /api/v1/auth/me` | Authenticated | Current user's safe profile |
| `GET /api/v1/products/**` | Public | Catalog reads |
| `GET /api/v1/categories` | Public | Active categories |
| `GET /actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**` | Public | Health and API documentation |
| `/api/v1/admin/**` | `ADMIN` | Existing catalog management endpoints |
| Other routes | Denied | Explicitly unavailable until designed |

## Persistence

Flyway `V2` creates `users` with a case-insensitive unique email, BCrypt hash, role check constraint, and creation timestamp. Hibernate validates this schema. Unique constraints remain authoritative when two registrations race.

## Files and effects

- Add `auth` DTOs, controller, service, token issuer, and optional admin bootstrap.
- Add `user` entity, role enum, and repository.
- Add JWT/security configuration and a `V2` migration.
- Update application configuration, dependencies, README, and focused unit tests.
- Catalog controllers and service rules stay unchanged; their `/admin` URLs become protected by the filter chain.

## Verification criteria

1. Registration stores a BCrypt hash and returns no password/hash.
2. Duplicate email returns `409`; invalid fields return `400`.
3. Valid login returns a signed token; invalid login returns generic `401`.
4. Valid tokens work at `/auth/me`; expired/invalid tokens are rejected.
5. Anonymous customer catalog reads remain public.
6. Anonymous calls to `/api/v1/admin/**` return `401`; customer tokens return `403`; admin tokens can perform catalog writes.
7. The bootstrap creates an admin only when both environment settings are present and never changes an existing account.
8. Flyway applies V2 and Hibernate validates the user mapping.
