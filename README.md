<p align="center">
	<img src="frontend/public/OverKill_Logobig.png" alt="OverKill Development logo" width="500" />
</p>
<p align="center"><strong>Mod shop, account linking, and secure download platform for OverKill products.</strong></p>

## About OverKillDevelopment
I built OverKillDevelopment to run my product ecosystem in one place: secure sign-in, paid access management, and controlled delivery of private mod downloads without relying on multiple disconnected services.

## Features
- Steam OpenID authentication with JWT-based sessions
- Account linking for Discord, GitHub, and Google
- Stripe checkout and subscription flows with idempotency support
- Purchase history, release gating, and access-code redemption
- Secure one-time download token flow for protected files
- Webhook safety with duplicate event protection and payment reconciliation
- User-owned template integrations and sync support
- Containerized local development with frontend/backend split

## Technology Stack
- **Backend:** Spring Boot (REST) + JPA/Hibernate
- **Frontend:** React + Vite
- **Database:** MySQL
- **Auth:** Steam OpenID + OAuth (Discord, GitHub, Google) + JWT
- **Payments:** Stripe Checkout + Webhooks
- **Build Tool:** Maven
- **Containerization:** Docker, Docker Compose
- **CI/CD:** Jenkins

## Architecture

OverKillDevelopment is a container-friendly full-stack app with a React frontend, Spring Boot API, and MySQL persistence, built around authentication, product ownership, and secure digital delivery.

### System Architecture

```mermaid
flowchart LR
	U[User Browser]

	subgraph PROD[Production]
		FE[Frontend Container\nNginx + React Build]
		BE[Backend Container\nSpring Boot API + JWT]
		DB[(MySQL)]
		STRIPE[Stripe API]
		STEAM[Steam OpenID]
		OAUTH[Discord and GitHub and Google OAuth]
		STORAGE[Protected Download Assets]
	end

	U -->|HTTPS| FE
	FE -->|/api| BE
	BE -->|JPA/Hibernate| DB
	BE -->|Checkout + Webhooks| STRIPE
	BE -->|Sign-in| STEAM
	BE -->|Account linking| OAUTH
	BE -->|Tokenized access| STORAGE

	subgraph DEV[Local Development]
		VITE[Vite Dev Server :5173]
		API[Spring Boot Dev :8080]
		DEVDB[(MySQL in Docker)]
	end

	U -. hot reload .-> VITE
	VITE -. /api proxy .-> API
	API -. JDBC .-> DEVDB
```

### Database Relationship Diagram

```mermaid
flowchart LR
    subgraph LEFT[ ]
        SHOP_ORDERS[SHOP_ORDERS\nFK: user_id]
        DOWNLOAD_TOKENS[DOWNLOAD_TOKENS\nFK: user_id]
        TIER_ZONE_MAPS[TIER_ZONE_MAPS\nFK: user_id]
    end

    USERS[USERS\nPK: id]

    subgraph RIGHT[ ]
        USER_TEMPLATES[USER_TEMPLATES\nFK: user_id]
        USER_TOKENS[USER_TOKENS\nFK: user_id]
        USER_GOOGLE_CREDENTIALS[USER_GOOGLE_CREDENTIALS\nFK: user_id]
        SHOP_ACCESS_CODES[SHOP_ACCESS_CODES\nFK: redeemed_by_user_id]
    end

    SHOP_ORDERS -->|many-to-one| USERS
    DOWNLOAD_TOKENS -->|many-to-one| USERS
    TIER_ZONE_MAPS -->|many-to-one| USERS

    USERS -->|one-to-many| USER_TEMPLATES
    USERS -->|one-to-many| USER_TOKENS
    USERS -->|one-to-one or one-to-many| USER_GOOGLE_CREDENTIALS
    USERS -->|one-to-many| SHOP_ACCESS_CODES
```
