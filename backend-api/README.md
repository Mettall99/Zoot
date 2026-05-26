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
