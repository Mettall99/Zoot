# Architecture

- `backend-api/src/app.ts`: middleware + routes + error handler.
- `backend-api/src/main.ts`: bootstrap listen.
- `backend-api/src/routes`: MVP endpoints.
- `backend-api/src/config/env.ts`: env validation via zod.
- `backend-api/src/db`: pg pool + migration runner.
- `android-client`: Gradle Android project with Compose UI, deeplink parser, protocol selector and tests.
- `admin-panel`: Next.js App Router minimal dashboard.
- `infra/docker-compose.yml`: postgres + redis + backend.
- `server-agent`: install + health-report scripts + systemd sample.
