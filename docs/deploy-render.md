# Deploy BookSmart on Render (free tier)

This guide deploys from the repo **Dockerfile** with **no secrets in git**. All keys live in the Render Dashboard.

## Security checklist

- Never commit `application.properties`, `.env`, or `*.db`.
- Set `BOOKING_ADMIN_PIN`, SMTP, and Twilio values only as **Environment Variables** in Render (marked `sync: false` in `render.yaml`).
- Use a **new** admin PIN for production (not your local dev PIN).
- Set `BOOKING_BASE_URL` to your public URL (`https://your-service.onrender.com`) so cancel links in email work.
- Start with `BOOKING_EMAIL_ENABLED=false` and `BOOKING_SMS_ENABLED=false` for a safe public demo; enable integrations later via env vars.

## Steps

1. Push this repo to GitHub.
2. In [Render](https://render.com) → **New** → **Blueprint** → connect the repo (uses root `render.yaml`).
3. When prompted, set **secret** env vars:
   - `BOOKING_BASE_URL` — e.g. `https://booksmart-xxxx.onrender.com` (update after first deploy if needed)
   - `BOOKING_ADMIN_PIN` — long random value
4. Deploy. Health check: `/actuator/health`.
5. Open `/admin`, sign in with your PIN, or use demo data from `DataSeeder` on first boot.

## Optional: email on Render

Render **blocks outbound SMTP** (port 587). Use Brevo’s **HTTPS API**, not SMTP, on Render:

1. Set `BOOKING_EMAIL_ENABLED=true`, `BOOKING_REMINDERS_ENABLED=true`, `BOOKING_REMINDERS_FROM` (verified Brevo sender).
2. In [Brevo](https://app.brevo.com) → **SMTP & API** → **API keys** → create a key (`xkeysib-…`).
3. Set `BREVO_API_KEY` in the Render dashboard (secret). Redeploy.
4. Startup logs should show `[EmailService] Ready — sending from … via Brevo HTTPS API.` and `[BrevoApiClient] Ready`.

Keep `SPRING_MAIL_*` for local dev if you prefer SMTP; on Render the app uses `BREVO_API_KEY` when it is set.

`ProdStartupChecks` requires `BOOKING_REMINDERS_FROM` plus either `BREVO_API_KEY` or complete SMTP vars.

## Optional: persistent SQLite

Free Render web services use **ephemeral** disk; the DB resets on redeploy (demo seed runs again). On a **paid** plan, attach a disk at `/data` and keep `BOOKING_DB_PATH=/data/booking.db`.

## Local Docker smoke test

```bash
cp .env.example .env
# Edit .env — set BOOKING_BASE_URL and BOOKING_ADMIN_PIN

docker build -t booksmart .
docker run --rm -p 8080:8080 --env-file .env booksmart
```

Do not commit `.env`.
