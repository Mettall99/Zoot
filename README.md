# Zooot VPN MVP Monorepo

## Services
- `android-client` — Android клиент
- `backend-api` — API (Node.js + TypeScript + PostgreSQL + Redis)
- `admin-panel` — админка (Next.js)
- `server-agent` — агент/скрипты для VPS
- `infra` — docker-compose и инфраструктурные шаблоны
- `docs` — архитектура, API и безопасность

## Backend quickstart
```bash
cd backend-api
cp .env.example .env
npm install
npm run db:migrate
npm run db:seed
npm run dev
```
