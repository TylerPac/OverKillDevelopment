# SolaceStudio — MySQL / Auth Setup

.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev



This repository contains a Spring Boot backend and a Vite/React frontend. I added a minimal MySQL-ready setup, JWT auth skeleton, Docker support, and a small frontend auth example.

**Files added**
- `.env` — template with env var names
- `docker-compose.yml` — MySQL + backend
- `backend/Dockerfile` — multi-stage build
- `backend` changes: JPA, Security, JWT, minimal `User` + `AuthController`
- `frontend/src/AuthExample.jsx` — React example component

**Quick overview**
- Local (host MySQL): set `SPRING_DATASOURCE_URL` to your local MySQL JDBC URL and run the backend with Maven or the IDE.
- Docker Compose: `docker-compose up --build` will start MySQL and the backend using the `.env` values. Ensure `.env` contains `MYSQL_PORT`, `BACKEND_PORT`, and your DB credentials; compose reads `.env` via `env_file`.
- AWS RDS: create an RDS MySQL instance, put the endpoint into `SPRING_DATASOURCE_URL`, and inject credentials from Secrets Manager into your runtime environment.

Environment variables
- See `.env` (template at repository root). Key variables:
  - `SPRING_DATASOURCE_URL` — JDBC URL (example: `jdbc:mysql://localhost:3306/solacestudio?useSSL=false&serverTimezone=UTC`)
  - `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
  - `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` (docker-compose)
  - `MYSQL_PORT` — host port to map to the MySQL container (used by `docker-compose.yml`)
  - `SPRING_JWT_SECRET` — JWT signing secret (use a strong random value in production)

Run locally (no Docker)
1. Create the database if needed: `CREATE DATABASE solacestudio;` and set user/password.
2. Set environment variables (example in PowerShell):

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/solacestudio?useSSL=false&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = 'password'
.
cd backend
# Use the provided dev env files so your local dev uses local DB and not production envs.

Windows PowerShell (load backend `.env.dev` then run backend and frontend):

```powershell
# Load backend .env.dev into environment variables for this terminal
Get-Content backend/.env.dev | ForEach-Object {
  if ($_ -match '^(.*?)=(.*)$') { Set-Item -Path Env:$($matches[1]) -Value $matches[2] }
}

# Run backend (dev profile)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# In another terminal, run frontend (Vite)
cd frontend
npm run dev
```

macOS / Linux (bash):

```bash
# export variables from backend/.env.dev into shell
export $(grep -v '^#' backend/.env.dev | xargs)

# Run backend
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &

# Run frontend in a separate terminal
cd ../frontend
npm run dev
```
```

Run with Docker Compose

```bash
# copy .env and fill values
docker-compose up --build
```

In the compose setup the backend connects to the `mysql` service at `mysql:${MYSQL_PORT}` and `docker-compose.yml` reads values from the `.env` file via `env_file`.

AWS RDS notes
- Create a MySQL RDS instance and a database `solacestudio` (or change the DB name).
- Use the RDS endpoint in `SPRING_DATASOURCE_URL` and enable SSL in production.
- Store DB credentials in AWS Secrets Manager and inject into ECS/EKS/EBS environment variables.

Auth endpoints (backend)
- `POST /auth/register` — JSON: `{ "username": "u", "password": "p" }` → returns `{ "token": "..." }`
- `POST /auth/login` — JSON: `{ "username": "u", "password": "p" }` → returns `{ "token": "..." }`

Example curl (login):

```bash
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"username":"user","password":"pass"}'
```

Frontend example
- `frontend/src/AuthExample.jsx` is a minimal React component that demonstrates `register` and `login` calls and stores the JWT in `localStorage`.

Do we need Flyway?
- Not strictly. For simple projects you can let Hibernate manage the schema during development (`spring.jpa.hibernate.ddl-auto=update`). For production and collaborative teams, use Flyway or Liquibase to version and apply migrations reliably. I can add Flyway migrations and enable it if you want.

Next steps I can do for you (pick any):
- Add Flyway and create initial migration SQL.
- Add a small frontend flow that uses the JWT to call a protected endpoint.
- Add AWS deployment notes (ECS/ECR or Elastic Beanstalk) and a sample Secrets Manager integration.
# Solace Studio Portfolio

**Run Backend/Frontend**
``` 
npm run dev
mvn spring-boot:run
``` 


***Initial Setup***
```
cd Solace Studio
```

**Backend**

from vscode spring initializer
package and artifact
```
dev.tylerpac
backend
```
**Frontend**
```
npm create vite@latest
java script + react compiler
```

## Centralized environment files (root)

Use root-level env files so backend and frontend share one source of truth:

- `.env.development` for local development
- `.env.production` for production builds/runtime

Frontend now reads env files from the repository root, and backend dev scripts load `.env.development` automatically.

Recommended root `.env.development` keys:

```dotenv
# Frontend
VITE_API_URL=http://localhost:8081
VITE_DEV_SERVER_PORT=5173

# Backend
SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/solacestudio?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=change_me
SPRING_JWT_SECRET=change_me

APP_AUTH_FRONTEND_BASE_URL=http://localhost:5173
APP_AUTH_VERIFICATION_TTL_MINUTES=60
APP_AUTH_RESET_TTL_MINUTES=30

APP_EMAIL_PROVIDER=log
APP_EMAIL_FROM=no-reply@solacestudio.dev
APP_AWS_REGION=us-east-1

APP_STRIPE_SECRET_KEY=sk_test_xxx
APP_STRIPE_WEBHOOK_SECRET=whsec_xxx
APP_SHOP_CURRENCY=usd
APP_SHOP_SUCCESS_URL=http://localhost:5173
APP_SHOP_CANCEL_URL=http://localhost:5173
```

**Dev quick start (single-command on Windows)**

- Start the backend (loads root `.env.development` and uses the `dev` profile):

```powershell
# from repository root
.\backend\run-dev.ps1
```

- Or, using cmd.exe:

```cmd
backend\run-dev.bat
```

- In another terminal start the frontend (Vite):

```bash
cd frontend
npm run dev
```

These scripts load environment variables from root `.env.development` (fallback: `backend/.env.dev`) and run backend with `spring.profiles.active=dev`.

Note: The `dev` profile will fall back to an in-memory H2 database if the configured MySQL instance is not reachable. If you want the backend to use your local MySQL, ensure MySQL is running and `backend/.env.dev` points to it (or set `SPRING_DATASOURCE_URL` in your environment).

## Stripe shop setup (backend + frontend)

This project now includes a Stripe Checkout-based shop flow tied to authenticated users.

### What it does
- Public product catalog endpoint: `GET /shop/products`
- Authenticated checkout session creation: `POST /shop/checkout-session`
- Authenticated order history (saved in DB): `GET /shop/orders`
- Stripe webhook endpoint (updates order status): `POST /shop/webhook`
- User profile stores `stripeCustomerId` so repeat purchases reuse the same Stripe customer.

### 1) Add Stripe keys
Set these environment variables for the backend:

```powershell
$env:APP_STRIPE_SECRET_KEY = "sk_test_..."
$env:APP_STRIPE_WEBHOOK_SECRET = "whsec_..."
$env:APP_SHOP_CURRENCY = "usd"
$env:APP_SHOP_SUCCESS_URL = "http://localhost:5173"
$env:APP_SHOP_CANCEL_URL = "http://localhost:5173"
```

You can place these in root `.env.development` (recommended) or `backend/.env.dev` (fallback).

### 2) Start backend and frontend

```powershell
# Terminal 1
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2
cd frontend
npm run dev
```

### 3) Start Stripe webhook forwarding (local)
Install Stripe CLI, then run:

```powershell
stripe listen --forward-to http://localhost:8081/shop/webhook
```

Stripe CLI will print a webhook signing secret (`whsec_...`). Use that value for `APP_STRIPE_WEBHOOK_SECRET`.

### 4) Test checkout flow
1. Register + verify + log in from the app.
2. Open `Shop` in the UI.
3. Click `Buy with Stripe` on a product.
4. Complete payment with Stripe test cards:
  - Success: `4242 4242 4242 4242` (any future expiry/CVC/ZIP)
  - Decline simulation: `4000 0000 0000 0002` (will produce payment failure events)
5. Return to app and check `Your Orders` in Shop.

If order status is still `PENDING`, wait a few seconds and refresh the Shop view; webhook events finalize it to `PAID`.

### Stripe test mode behavior notes
- `payment_intent.payment_failed` and `charge.failed` are expected when using decline test cards.
- A `200` response from `/shop/webhook` means your backend received the Stripe event correctly.
- In test mode, card numbers intentionally control the scenario (success vs decline) for safe testing.

## Security and reliability hardening

The backend now includes:

- Access + refresh token strategy (`POST /auth/login` returns both, `POST /auth/refresh` rotates refresh token)
- Login protection with in-memory rate limiting and brute-force lockout
- Stripe checkout idempotency support via `Idempotency-Key` header
- Webhook duplicate-delivery safety via persisted processed event IDs
- Payment failure handling (`payment_intent.payment_failed`, `charge.failed`) marking orders as `FAILED`
- Scheduled reconciliation job for pending orders (`APP_SHOP_RECONCILE_INTERVAL_MS`)
- Purchase emails for pending/paid/failed states

## Production secret management checklist

Before public launch:

1. Rotate all API credentials used during local testing.
2. Set `APP_SECURITY_ALLOW_WEAK_JWT_SECRET=false` in production.
3. Use a strong `SPRING_JWT_SECRET` (32+ chars, random).
4. Keep secrets only in environment/secret store (AWS Secrets Manager, Azure Key Vault, etc.).
5. Do not commit `.env.development`/`.env.production` with real secrets.
6. Keep SES in production mode (not sandbox) or use another provider.
