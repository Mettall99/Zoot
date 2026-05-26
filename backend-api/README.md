# Backend API

## Run
```bash
cp .env.example .env
npm install
npm run dev
```

## Migrations
```bash
psql "$DATABASE_URL" -f migrations/001_init.sql
```

## Tests
```bash
npm test
```
