# Zooot VPN Architecture (MVP)

## Components
- Android app получает token из deep link, резолвит в backend, выбирает server/protocol, запускает VPN adapter.
- Backend API управляет auth/subscription/device/config issuance/session tracking.
- Admin panel управляет users/servers/protocols/tariffs.
- Server agent устанавливает protocol cores и шлёт health/load.

## Phases
- MVP1: auth, tariffs/subscriptions, servers/protocols, token resolve, WireGuard manual, auto select by load.
- MVP2: AmneziaWG, XRay, fallback, health history, device limit.
- MVP3: auto-install, OpenVPN, analytics, key rotation, push.
