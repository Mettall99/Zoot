# Security baseline

- TLS only behind Nginx/Caddy.
- JWT access short-lived, refresh rotation.
- Password hashing via argon2.
- Config token is pointer-only (no plaintext VPN config in token).
- Device limit enforcement by tariff.
- Rate limiting on auth and token resolve.
- Payment webhook signature verification.
- Admin audit log for mutations.
- Secrets through env/secret manager.
