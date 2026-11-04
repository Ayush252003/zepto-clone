# Authentication and authorization

## Sign-in flow

`POST /api/v1/auth/register` creates a CUSTOMER account. Passwords are stored as BCrypt hashes. `POST /api/v1/auth/login` verifies the password and returns a 15-minute HS256 JWT; `GET /api/v1/auth/me` returns the authenticated user's safe profile.

The JWT signing key comes from `JWT_SECRET_BASE64` and must decode to at least 32 bytes. The token carries the issuer and role. Spring Security validates the signature, expiry, and issuer before applying the route rules.

## Route access

| Route | Access |
| --- | --- |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | Public |
| `GET /api/v1/auth/me` | Any authenticated user |
| Product/category reads, health, Swagger/OpenAPI | Public |
| `/api/v1/cart/**`, `/api/v1/orders/**`, `/api/v1/notifications/**` | CUSTOMER |
| `/api/v1/admin/**` | ADMIN |
| Any unlisted route | Denied |

An initial administrator can be created by setting both `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` at startup. The bootstrap only inserts a missing account; it does not promote or overwrite an existing user. Remove the bootstrap values after first use.

## Trade-offs and gaps

JWTs avoid server-side sessions and fit the current single-process API. Because signing uses a shared HMAC key, any future service that validates tokens would also be able to mint them. If the app is split into services, revisit asymmetric signing and key distribution.

There is no refresh-token flow, immediate token revocation, password reset, or login throttling. A role change can therefore take up to the token's remaining 15-minute lifetime to affect an already issued token. These gaps should be addressed before using the API for real customer accounts.

Flyway migration `V2` creates the users table with a case-insensitive unique email and a role constraint. The database constraint remains the final defense against two concurrent registrations with the same address.
