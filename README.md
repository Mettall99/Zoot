# Zooot VPN MVP Monorepo

## Modules
- `android-client` — Android app (Kotlin/Compose)
- `backend-api` — Express + TypeScript API
- `admin-panel` — Next.js admin UI
- `server-agent` — server scripts/systemd sample
- `infra` — docker-compose for local stack
- `docs` — architecture/openapi/security/examples

## Quick start
1. Backend:
```bash
cd backend-api
cp .env.example .env
npm install
npm run build
npm test
npm run dev
```
2. Admin panel:
```bash
cd admin-panel
cp .env.example .env.local
npm install
npm run build
npm run dev
```
3. Infra:
```bash
cd infra
cp .env.example .env
docker compose up --build
curl http://localhost:3000/health
```
4. Android:
```bash
cd android-client
./gradlew test
```
