# Online Voting System

Spring Boot 3 backend with a plain HTML/CSS/JS frontend. Voters sign in with a college email and a one-time code, then cast one vote per election. Admins manage elections, candidates, the voter list and result visibility.

## How it works

1. A voter enters their college email. A 6-digit code (BCrypt-hashed in the database, valid 5 minutes, 5 attempts) is emailed.
2. After the code is verified they receive a JWT (60 minutes) and see the elections they are on the list for.
3. One vote per person per election, enforced by a database unique constraint on `(election_id, user_id)`, so races cannot create two votes.
4. Admins see who voted and when, never whom they voted for.

Result visibility is set per election: `LIVE`, `ON_CLOSE`, `SCHEDULED` (a fixed date and time) or `MANUAL` (admin presses Publish). Admins always see live counts.

All emails are trimmed and lowercased everywhere (sign-in, CSV upload, config), so `Aarav@College.edu` and `aarav@college.edu` are the same person.

## Run it locally (2 minutes)

Requires Java 17+ and Maven.

```bash
mvn spring-boot:run
```

Open http://localhost:8080. The dev profile uses an in-memory H2 database, so data is lost on restart.

With no SMTP configured in dev, the sign-in code is **printed in the console**, so you can test without email.

### Send real OTP emails
1. Copy `.env.example` to `.env` in the project folder.
2. Fill in `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` (for Gmail: enable 2-Step Verification, then create an App Password at https://myaccount.google.com/apppasswords).
3. Set `COLLEGE_DOMAIN` and `ADMIN_EMAILS`. Codes are only sent to addresses ending in `COLLEGE_DOMAIN` that are an admin or on a voter list, so to test with Gmail use `COLLEGE_DOMAIN=gmail.com`.
4. Start the app. The console shows `Email is configured: ...` when it worked. If it says `NOT configured`, the `.env` file was not found (it must sit where you run `mvn`).

### Candidate photos
In Admin > Manage > Candidates, choose a JPG, PNG, WebP or GIF (max 2 MB) when adding a candidate, or use Change photo later. Photos are stored in the database, so they survive redeploys. A photo can only be changed before the first vote is cast. Sign in as the admin address in `application.properties` (`admin@yourcollege.edu` by default) to reach the admin page.

Run the tests with `mvn test`.

## Configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` (H2) or `prod` (real DB) | `dev` |
| `COLLEGE_DOMAIN` | Only emails ending in this can sign in | `yourcollege.edu` |
| `ADMIN_EMAILS` | Comma-separated admin emails | `admin@yourcollege.edu` |
| `JWT_SECRET` | 32+ random characters. **Required in prod** | dev only |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:mysql://...` or `jdbc:postgresql://...` | |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | Mail server for the codes | |
| `MAIL_FROM` | From address (Gmail needs the same as `SMTP_USER`) | `SMTP_USER` |
| `DISPLAY_ZONE` | Time zone used in server-written messages | `Asia/Kolkata` |
| `CORS_ORIGINS` | Only needed if the frontend is hosted elsewhere | empty |

## Running an election

1. Sign in as admin, create an election (window, result mode, restricted to list or open).
2. Add candidates. Upload the class list (`voters.csv` shows the format). Emails must be on the college domain.
3. Share the site link. Voters sign in and vote.
4. Watch turnout under Turnout, download the voted and not-voted lists, and publish results as you chose.

Notes: a candidate list locks after the first vote. An election with votes cannot be deleted. Every admin action is written to the audit log.

## API

Swagger UI: `/swagger-ui.html`. Main routes:

```
POST /api/auth/request-otp            POST /api/auth/verify-otp
GET  /api/elections                   GET  /api/elections/{id}/candidates
POST /api/elections/{id}/vote         GET  /api/elections/{id}/my-status
GET  /api/elections/{id}/results      GET  /api/elections/{id}/results/stream   (SSE, LIVE only)

/api/admin/elections ...              create, edit, delete, close
/api/admin/elections/{id}/candidates  add; PUT/DELETE /api/admin/candidates/{id}
/api/admin/elections/{id}/voters/upload | eligible | voted | not-voted | export?type=
/api/admin/elections/{id}/turnout
/api/admin/elections/{id}/result-settings | publish-results | results | results/export
/api/admin/audit-logs
```

Sample:

```bash
curl -X POST localhost:8080/api/auth/request-otp -H 'Content-Type: application/json' -d '{"email":"admin@yourcollege.edu"}'
curl -X POST localhost:8080/api/auth/verify-otp  -H 'Content-Type: application/json' -d '{"email":"admin@yourcollege.edu","otp":"123456"}'
curl localhost:8080/api/elections -H "Authorization: Bearer <token>"
```

## Known limits

- Rate limiting is in memory: it resets on restart and is per server instance.
- Votes are linked to users (auditable, simple). For fully anonymous ballots, split `votes` into a "has voted" table and a separate ballot table.
- Free hosts may sleep when idle. Open the site a few minutes before voting starts.
- Never run a real election on plain HTTP. Render and Cloud Run provide HTTPS.

Deployment steps are in [DEPLOY.md](DEPLOY.md).
