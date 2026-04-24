# Frontend (Vite + React)

## Environment

Set this in `frontend/.env.development` for local development:

```dotenv
VITE_API_BASE_URL=http://localhost:8081
```

## Auth flow

The frontend uses backend-driven OAuth routes.

1. Sign-in starts with `GET /auth/steam/login-url`.
2. The backend redirects to `/steam-callback` with session data.
3. After sign-in, users can link Discord from the dashboard.
4. Discord OAuth redirects to `/discord-callback`.

Username/password registration and login are not used.
