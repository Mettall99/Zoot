# Security (current MVP)

Implemented now:
- Helmet, CORS, JSON parser, request logging.
- Zod request validation + unified error format.
- Auth endpoints rate-limited.
- Argon2 password hashing.
- JWT secrets from environment.

Current limitations:
- In-memory/demo auth flow (no persisted users yet).
- No refresh token rotation store yet.
- No VPN config issuing encryption pipeline yet.
