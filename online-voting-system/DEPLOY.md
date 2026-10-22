# Deploying for a real election

You need three things: the app (Docker), a database, and a way to send email. Do a full test with two or three friends before the real vote.

## 0. Before anything

1. Push this folder to a private GitHub repository (`.gitignore` is included).
2. Generate a JWT secret: `openssl rand -base64 48`.
3. Choose an email service. Gmail works: turn on 2-step verification, then create an **App password** at https://myaccount.google.com/apppasswords.
   - `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, `SMTP_USER=you@gmail.com`, `SMTP_PASSWORD=<app password>`
   - Gmail limits sending to roughly 500 emails a day, which is enough for a class. Brevo's free plan (`smtp-relay.brevo.com`) is a good alternative.
4. Create a database (below). Use MySQL or PostgreSQL, never H2, for a real election.

## Option A: Render + free Postgres (no billing setup)

1. Create a free PostgreSQL database on Neon (https://neon.tech) or Aiven. Copy host, database, user and password.
2. Build the JDBC URL: `jdbc:postgresql://HOST/DBNAME?sslmode=require`
3. On https://render.com choose New, then Web Service, connect your GitHub repo, and select **Docker** as the runtime.
4. Add these environment variables:

```
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://HOST/DBNAME?sslmode=require
DB_USERNAME=...
DB_PASSWORD=...
JWT_SECRET=...
COLLEGE_DOMAIN=yourcollege.edu
ADMIN_EMAILS=you@yourcollege.edu
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=...
SMTP_PASSWORD=...
```

5. Deploy. Render gives you `https://your-app.onrender.com`. Free instances sleep when idle, so open the link a few minutes before voting starts (or use a paid instance that stays on).

## Option B: Google Cloud Run

```bash
gcloud auth login && gcloud config set project YOUR_PROJECT_ID
gcloud run deploy online-voting --source . --region asia-south1 --allow-unauthenticated \
  --min-instances 1 --timeout 3600 \
  --set-env-vars SPRING_PROFILES_ACTIVE=prod,COLLEGE_DOMAIN=yourcollege.edu,ADMIN_EMAILS=you@yourcollege.edu \
  --set-env-vars DB_URL='jdbc:postgresql://HOST/DBNAME?sslmode=require',DB_USERNAME=...,DB_PASSWORD=... \
  --set-env-vars JWT_SECRET=...,SMTP_HOST=smtp.gmail.com,SMTP_USER=...,SMTP_PASSWORD=...
```

- Billing must be enabled on the Google Cloud project. Cloud Run has a free tier, but `--min-instances 1` is billed.
- Cloud SQL is not free. Using an external free Postgres (Neon) keeps costs near zero.
- Put secrets in Secret Manager if you prefer: `--set-secrets JWT_SECRET=jwt:latest`.
- Cloud Run runs several instances at once on busy days. Rate limiting is per instance, which is fine at class scale.

## Option C: your own machine or VPS with Docker Compose

```bash
cp .env.example .env     # fill it in
docker compose up --build -d
```

Serves on port 8080 with MySQL. Put it behind HTTPS (for example Caddy or a Cloudflare Tunnel) before sharing the link.

## Election day checklist

- [ ] Sign in as admin and send yourself a code (confirms email works from the hosted site).
- [ ] Create the election, add candidates, upload the voter CSV. Check the "On the list" count matches your class.
- [ ] Set the result mode (Live, On close, Scheduled or Manual).
- [ ] Vote once from a test account in a separate election, then delete that election.
- [ ] Share the link and QR code. Remind people to check spam for the code.
- [ ] After voting closes, download the voted list and results CSVs for your records.

## Troubleshooting

- **No code arrives:** check the app logs for "Could not send the sign-in code". Usually the SMTP user or app password is wrong, or `MAIL_FROM` differs from the Gmail address.
- **App fails at startup with "JWT secret missing":** set `JWT_SECRET` to 32+ characters.
- **Database connection error:** check the JDBC URL prefix (`jdbc:postgresql://` or `jdbc:mysql://`) and that the host allows outside connections with SSL.
- **A voter says "not on the list":** the email in the CSV must match exactly after lowercasing, with no typos in the domain.
