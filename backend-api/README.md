# Backend API

## Run
```bash
cd backend-api
cp .env.example .env
npm install
npm run db:migrate
npm run db:seed
npm run dev
```

## Check
```bash
curl http://localhost:3000/health

curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'

curl -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@zooot.local","password":"password123"}'

curl -X POST http://localhost:3000/api/v1/config/resolve-token \
  -H "Content-Type: application/json" \
  -d '{"token":"demo-token"}'
```

## WireGuard demo config (MVP)

Backend can optionally include WireGuard client config in `POST /api/v1/config/resolve-token`.

Set in `.env`:

```bash
WIREGUARD_CLIENT_CONFIG_PATH=/app/runtime/wireguard/clients/demo.conf
```

Behavior:
- empty `WIREGUARD_CLIENT_CONFIG_PATH` -> `config: null`;
- missing/inaccessible file -> `config: null` (backend keeps working);
- max file size is limited to 64 KB;
- backend never logs config contents.
