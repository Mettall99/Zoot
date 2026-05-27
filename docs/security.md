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

## Security notes
- Demo key must be rotated before production.
- Never log private keys or full config.
- WireGuard configs should stay `chmod 600`.
- Prefer read-only backend mount for clients when backend only reads configs.
