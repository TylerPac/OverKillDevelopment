# OverKillDevelopment

This repository uses a local-first full stack workflow:

- frontend runs locally with Vite + React
- backend runs locally with Spring Boot
- MySQL runs in Docker for development

## Environment Separation

Frontend env files:

- frontend/.env.development
- frontend/.env.production

Backend env files:

- backend/.env.development
- backend/.env.production

No shared root .env file is used by frontend/backend in development.

## Frontend

Only VITE_ prefixed env vars are supported by Vite.

Required key:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Frontend API calls use import.meta.env.VITE_API_BASE_URL.

## Backend Profiles

Database profiles:

- dev-mysql
- prod

Optional local fallback profile:

- dev-h2

Profile config files:

- backend/src/main/resources/application-dev-mysql.properties
- backend/src/main/resources/application-prod.properties
- backend/src/main/resources/application-dev-h2.properties

Backend env keys:

```dotenv
DB_URL=jdbc:mysql://localhost:3306/tylerpac_dev
DB_USERNAME=root
DB_PASSWORD=password
SPRING_PROFILES_ACTIVE=dev-mysql
JWT_SECRET=replace_me_with_a_32_plus_char_secret
STRIPE_SECRET_KEY=replace_me
```

## Development Workflow

1. Start MySQL (Docker)

```bash
docker compose -f docker-compose.dev.yml up -d
```

2. Run backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-mysql
```

3. Run frontend

```bash
cd frontend
npm run dev
```

## VS Code Tasks

Tasks are available for:

- Start MySQL (Docker)
- Run Backend (dev-mysql)
- Run Frontend
- Run Development Environment (parallel)

See .vscode/tasks.json.

## CI/CD Compatibility

This setup is ready for Docker-backed CI/CD:

- deterministic MySQL service via docker-compose.dev.yml
- explicit Spring profiles per environment
- no mixed frontend/backend env loading

## Optional helpers

- backend/.env.example
- frontend/.env.example
- scripts/reset-dev-mysql-volume.bat

## Security and reliability hardening

The backend now includes:

- Steam OpenID sign-in flow (`GET /auth/steam/login-url`, `GET /auth/steam/callback`)
- Discord account linking flow (`GET /auth/discord/link-url`, `GET /auth/discord/callback`)
- Access + refresh token strategy (`POST /auth/refresh` rotates refresh tokens)
- Login protection with in-memory rate limiting and brute-force lockout
- Stripe checkout idempotency support via `Idempotency-Key` header
- Webhook duplicate-delivery safety via persisted processed event IDs
- Payment failure handling (`payment_intent.payment_failed`, `charge.failed`) marking orders as `FAILED`
- Scheduled reconciliation job for pending orders (`APP_SHOP_RECONCILE_INTERVAL_MS`)
- Purchase event logging for pending/paid/failed states

## Production secret management checklist

Before public launch:

1. Rotate all API credentials used during local testing.
2. Set `APP_SECURITY_ALLOW_WEAK_JWT_SECRET=false` in production.
3. Use a strong `JWT_SECRET` (32+ chars, random).
4. Keep secrets only in environment/secret store (AWS Secrets Manager, Azure Key Vault, etc.).
5. Do not commit `.env.development`/`.env.production` with real secrets.
6. Configure Steam and Discord OAuth credentials in production environment variables.



scp "E:\OVERKILLMODS\keycard-crates.zip" ubuntu@15.204.118.134:/opt/overkill/downloads/keycard-crates.zip

scp "E:\OVERKILLMODS\weapon-system.zip" ubuntu@15.204.118.134:/opt/overkill/downloads/weapon-system.zip
scp "E:\OVERKILLMODS\battle-pass.zip" ubuntu@15.204.118.134:/opt/overkill/downloads/battle-pass.zip