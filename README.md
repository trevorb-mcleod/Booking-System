# BookSmart — Appointment Booking System

A self-hosted appointment booking system built with Spring Boot, Thymeleaf, and SQLite.

## Features

- **Customer portal** — book appointments, choose a provider (or "Any Available"), pick a date/time, receive email confirmation with a one-click cancellation link
- **Staff portal** — manage providers, services, clients, and appointments via a PIN-protected dashboard
- **Email notifications** — confirmation, 24-hour reminder, and cancellation emails via any SMTP provider (Brevo recommended)
- **Self-service cancellation** — customers cancel via a secure token link without needing an account
- **SQLite persistence** — all data survives restarts; no external database required

## Quick Start

### 1. Configure

Copy the example config and fill in your values:

```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

Edit `application.properties`:

| Key | What to set |
|-----|-------------|
| `booking.business-name` | Your business name |
| `booking.base-url` | Public URL (e.g. `https://yourdomain.com`) — used in email cancel links |
| `booking.admin.pin` | PIN for the staff portal at `/admin` |
| `booking.reminders.from` | Verified sender email address |
| `spring.mail.username` | Brevo SMTP login |
| `spring.mail.password` | Brevo SMTP key |

### 2. Run

```bash
./start.sh
```

The app starts on **http://localhost:8080** by default.

| URL | Description |
|-----|-------------|
| `/` | Customer home page |
| `/book` | Customer booking form |
| `/admin` | Staff portal (PIN required) |

### 3. First-time setup (Staff Portal)

1. Go to `/admin` and enter the PIN from `application.properties`
2. Add **Services** (name, duration, price)
3. Add **Providers** and assign them services + working hours

## Email Setup (Brevo)

1. Sign up at [brevo.com](https://brevo.com) (free tier: 300 emails/day)
2. Verify your sender email under **Senders & IPs → Senders**
3. Go to **SMTP & API → SMTP Keys** and generate a key
4. Set `spring.mail.username` to the SMTP login shown on that page
5. Set `spring.mail.password` to the generated key
6. Set `booking.reminders.from` to your verified sender address

## Deployment Notes

- `booking.db` is created automatically in the working directory — back it up regularly
- Set `booking.base-url` to your public domain so cancellation links in emails work correctly
- Never commit `application.properties` or `booking.db` — both are gitignored

## Room for Improvement

This is intentionally a clean, focused base package. The core booking workflow is complete, but there is plenty of room to extend it without over-engineering. Some ideas:

- **SMS reminders** — integrate Twilio or a similar provider to send text reminders alongside email
- **Revenue analytics** — charts and summaries on the admin dashboard (monthly revenue, busiest days, top services)
- **Online payments** — Stripe integration to collect deposits or full payment at booking time
- **Customer accounts** — let returning customers log in to view and manage their appointment history
- **Multi-location support** — separate providers and schedules by physical location
- **Waitlist** — allow customers to join a waitlist when a time slot is fully booked
- **Calendar sync** — two-way Google/Outlook calendar integration for providers

That said, the app is production-ready as-is for most small-business use cases. Adding every feature risks turning a simple, maintainable tool into something unnecessarily complex.

## Tech Stack

- Java 21+ / Spring Boot 3
- Thymeleaf (server-side templates)
- Spring Data JPA + SQLite
- Bootstrap 5 + Bootstrap Icons
- Spring Mail (JavaMailSender)

