# Local Setup Guide: Online Voting System

A complete, start-from-zero guide to running this project on your own computer, testing a full election, and publishing the code to GitHub.

**What you will have at the end:** the app running at `http://localhost:8080`, an admin account, a test election with candidates, a test voter who casts a vote, and the project safely pushed to your GitHub account.

---

## Table of contents

1. [What this project is made of](#1-what-this-project-is-made-of)
2. [Install the required tools](#2-install-the-required-tools)
3. [Get the project on your computer](#3-get-the-project-on-your-computer)
4. [Run it (fastest path, about 2 minutes)](#4-run-it-fastest-path)
5. [Configure it properly with a `.env` file](#5-configure-it-with-a-env-file)
6. [Walkthrough: run a test election end to end](#6-walkthrough-run-a-test-election-end-to-end)
7. [Receiving real OTP emails (optional)](#7-receiving-real-otp-emails-optional)
8. [Run with Docker Compose (optional)](#8-run-with-docker-compose-optional)
9. [Run the automated tests](#9-run-the-automated-tests)
10. [Useful URLs, stopping, and resetting](#10-useful-urls-stopping-and-resetting)
11. [Troubleshooting](#11-troubleshooting)
12. [Put the project on GitHub](#12-put-the-project-on-github)
13. [Cloning it on another computer](#13-cloning-it-on-another-computer)

---

## 1. What this project is made of

| Part | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.3.5, Spring Security (JWT), Spring Data JPA |
| Frontend | Plain HTML, CSS and JavaScript, served by the same Spring Boot app (no separate frontend server, no Node.js needed) |
| Database (local) | **H2**, a file-based database stored in `./data/`. Nothing to install. |
| Database (production / Docker) | MySQL 8.4 or PostgreSQL |
| Build tool | Maven |
| API docs | Swagger UI |

Because the frontend ships inside the backend, there is **one command to start everything**.

### Project layout

```
online_voting_system/                 <- repository root
├── README.md                         <- project overview
├── LOCAL_SETUP.md                    <- this guide
└── online-voting-system/             <- the actual application (run commands here)
    ├── pom.xml                       <- Maven build file
    ├── .env.example                  <- template for your settings
    ├── .gitignore
    ├── Dockerfile
    ├── docker-compose.yml
    ├── DEPLOY.md                     <- production deployment notes
    ├── voters.csv                    <- sample voter list
    └── src/
        ├── main/java/com/example/voting/   <- controllers, services, entities, security
        ├── main/resources/
        │   ├── application*.properties     <- settings (base, dev, prod)
        │   └── static/                     <- the web pages (index, vote, results, admin)
        └── test/                           <- automated tests
```

> **Important:** every `mvn` command in this guide must be run from inside the **`online-voting-system/online-voting-system`** folder, the one that contains `pom.xml`. If you see `The goal you specified requires a project to execute but there is no POM`, you are one folder too high.

---

## 2. Install the required tools

You need three things for the basic setup: **Java 17**, **Maven**, and **Git**. Docker is optional.

| Tool | Needed for | Required? |
| --- | --- | --- |
| Java JDK 17 (21 also works with Spring Boot 3.3) | Running and building the app | Yes |
| Apache Maven 3.9+ | Building and starting the app (the project has no Maven wrapper) | Yes |
| Git | Pushing to GitHub | Yes |
| A code editor (VS Code, IntelliJ IDEA) | Reading or editing code | Recommended |
| Docker Desktop | Running with MySQL via Docker Compose | Optional |

### 2.1 Install Java 17

Install a **JDK**, not just a JRE. Any of these distributions is fine; Eclipse Temurin is a good default.

**Windows**
1. Go to <https://adoptium.net> and download **Temurin 17 (LTS)** for Windows (`.msi`).
2. Run the installer. On the "Custom Setup" screen, enable **"Set JAVA_HOME variable"** and **"Add to PATH"**.
3. Finish the installer, then **close and reopen** any terminal windows.

Or with the Windows package manager:
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

**macOS** (with [Homebrew](https://brew.sh))
```bash
brew install --cask temurin@17
```

**Ubuntu / Debian Linux**
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
```

**Verify:**
```bash
java -version
```
You should see something like `openjdk version "17.0.x"`. If it says a lower number (for example `1.8`), another Java is first on your PATH; fix `JAVA_HOME`/PATH before continuing.

### 2.2 Install Maven

**Windows**
```powershell
winget install Apache.Maven
```
If `winget` is unavailable: download the "Binary zip archive" from <https://maven.apache.org/download.cgi>, unzip it (for example to `C:\tools\apache-maven-3.9.x`), then add its `bin` folder to your **PATH** (Start menu, search "Environment Variables", edit `Path`, add `C:\tools\apache-maven-3.9.x\bin`). Also make sure `JAVA_HOME` points to your JDK folder (for example `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`).

**macOS**
```bash
brew install maven
```

**Ubuntu / Debian Linux**
```bash
sudo apt install -y maven
```

**Verify** (open a **new** terminal first):
```bash
mvn -version
```
The output must show Maven 3.9+ **and** `Java version: 17`. If Maven reports a different Java, set `JAVA_HOME` to the JDK 17 folder.

### 2.3 Install Git

**Windows:** `winget install Git.Git` (or download from <https://git-scm.com/download/win>). This also installs **Git Bash**, handy for the `openssl` command used later.
**macOS:** `brew install git` (or run `git --version` and accept the Xcode tools prompt).
**Ubuntu / Debian:** `sudo apt install -y git`

**Verify and set your identity** (used for commit authorship):
```bash
git --version
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```
Use the same email as your GitHub account (or GitHub's private `noreply` address from *Settings, Emails*).

### 2.4 (Optional) Install Docker Desktop

Only needed for [section 8](#8-run-with-docker-compose-optional). Download from <https://www.docker.com/products/docker-desktop/>. Verify with `docker --version` and `docker compose version`.

---

## 3. Get the project on your computer

1. Unzip the file you downloaded (`online_voting_system-main.zip`). You get a folder named `online_voting_system-main`.
2. Optional but recommended: rename it to `online_voting_system` so it matches the repository name.
3. Open a terminal **inside the application folder** (the one containing `pom.xml`):

**Windows (PowerShell)**
```powershell
cd C:\path\to\online_voting_system\online-voting-system
dir pom.xml
```

**macOS / Linux**
```bash
cd /path/to/online_voting_system/online-voting-system
ls pom.xml
```

If `pom.xml` is listed, you are in the right place.

> Tip: on Windows you can open the folder in File Explorer, click the address bar, type `powershell`, and press Enter to open a terminal already in that folder.

---

## 4. Run it (fastest path)

### 4.1 Why you must set a domain first

The app only lets people sign in with an email whose **domain** is allowed (setting `COLLEGE_DOMAIN`). It also performs a **DNS check** (MX/A records) to make sure the domain really exists, which needs an internet connection.

The built-in placeholder domain `yourcollege.edu` is not a real mail domain you control, and the DNS check can reject it. So for local testing, use a **real domain such as `gmail.com`** and your own address as the admin. In local dev mode the sign-in code is printed in the terminal, so the address does not have to receive any email.

### 4.2 Start the app

Replace `you@gmail.com` with any address on the domain you choose.

**Windows (PowerShell)**
```powershell
$env:COLLEGE_DOMAIN = "gmail.com"
$env:ADMIN_EMAILS   = "you@gmail.com"
mvn spring-boot:run
```

**macOS / Linux**
```bash
COLLEGE_DOMAIN=gmail.com ADMIN_EMAILS=you@gmail.com mvn spring-boot:run
```

The first run downloads all dependencies from Maven Central, which can take a few minutes. Later runs start in seconds.

### 4.3 Confirm it started

Look for lines like these in the terminal:

```
Started VotingApplication in 6.2 seconds
Admin account ready: you@gmail.com
Email is NOT configured: sign-in codes are only printed in this console (dev). ...
```

The "Email is NOT configured" warning is **expected and fine** in local development; it means codes will be printed in the terminal.

Now open **<http://localhost:8080>** in your browser. You should see the **"Sign in to vote"** page.

To stop the app, press `Ctrl + C` in the terminal.

> Settings passed as environment variables like this last only for that terminal window. For a permanent setup, use a `.env` file (next section).

---

## 5. Configure it with a `.env` file

A `.env` file keeps your settings in one place so you don't retype them. The app automatically reads a file named `.env` from the folder you start it in (the folder containing `pom.xml`). It is already listed in `.gitignore`, so it will not be committed.

### 5.1 Minimal local `.env` (recommended)

Create a file named `.env` in `online-voting-system/` with just this:

```properties
COLLEGE_DOMAIN=gmail.com
ADMIN_EMAILS=you@gmail.com
```

Then run:
```bash
mvn spring-boot:run
```

### 5.2 Do not copy `.env.example` unedited

`.env.example` contains **placeholder SMTP settings** (`SMTP_HOST=smtp.gmail.com`, a fake user and password). If you copy it as-is, the app believes email is configured, tries to send a real email with fake credentials, and sign-in fails with *"Failed to deliver email"*.

Either:
- use the minimal `.env` above (no `SMTP_*` lines), **or**
- if you copy `.env.example`, delete or blank out the `SMTP_*` lines unless you are setting up real email ([section 7](#7-receiving-real-otp-emails-optional)).

### 5.3 All settings

| Variable | Meaning | Default |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` (H2 file database, OTP printed to console) or `prod` (real database, real email required) | `dev` |
| `COLLEGE_DOMAIN` | Comma-separated email domains allowed to sign in, e.g. `gmail.com` or `college.edu, student.college.edu` | `yourcollege.edu` |
| `ADMIN_EMAILS` | Comma-separated admin emails. Created at startup; there is no admin sign-up page. | `admin@yourcollege.edu` |
| `JWT_SECRET` | 32+ character secret for login tokens. **Not needed in dev** (a dev-only secret is built in). **Required** in `prod`/Docker. | dev only |
| `JWT_EXPIRY_MINUTES` | How long a login lasts | `60` |
| `PORT` | Web server port | `8080` |
| `DISPLAY_ZONE` | Time zone used when showing times in messages | `Asia/Kolkata` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | Mail server for real OTP emails | empty / `587` |
| `MAIL_FROM` | "From" address (Gmail requires it to equal `SMTP_USER`) | `SMTP_USER` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection (prod only) | none |
| `DDL_AUTO` | Hibernate schema mode | `update` |

Built-in limits (not environment variables): OTP is 6 digits, expires in 5 minutes, allows 5 wrong attempts; each email may request 3 codes per 10 minutes.

> **Changing admins or domains:** admin accounts are created at startup, so restart the app after editing `ADMIN_EMAILS`.

---

## 6. Walkthrough: run a test election end to end

This confirms everything works. You need the app running and the terminal visible, because **sign-in codes appear in the terminal**.

### Step 1: Sign in as admin

1. Open <http://localhost:8080>.
2. Enter your admin email (the one in `ADMIN_EMAILS`) and click **Send code**.
3. Look at the terminal for a line like:
   ```
   WARN ... SMTP not configured (dev). OTP for you@gmail.com is 483920
   ```
4. Type that 6-digit code and click **Verify and continue**.
5. You land on the **Election administration** page.

### Step 2: Create an election (open to the domain, simplest)

In the *Create election* form:

| Field | Suggested value |
| --- | --- |
| Title | `Class Representative 2026` |
| Voting opens | A time **a few minutes ago** (so voting is already open) |
| Voting closes | Later today or tomorrow |
| Show results to voters | **Live, while voting is open** (easiest for testing) |
| How should voters be authorized? | **Option 1: Vote by College Mail ID Domain** |
| Allowed domains | `gmail.com` (or leave blank to use `COLLEGE_DOMAIN`) |

Click **Create election**. The end time must be after the start time.

### Step 3: Add candidates

Click **Manage** on the election, open the candidates tab, and add two or more candidates (name, optional description, optional photo: JPG, PNG, WebP or GIF up to 2 MB). Candidates can only be added or changed before any vote is cast.

### Step 4: Vote as a test voter

1. Sign out (or open a **private/incognito window**).
2. Sign in with **a different address on the allowed domain**. A handy trick with Gmail-style domains: `you+voter1@gmail.com`. In dev mode the code is printed in the terminal, so the address does not need to receive mail.
3. Enter the code from the terminal.
4. On **Your elections**, choose a candidate, click **Review your choice**, then **Confirm vote**.
5. Try voting again; the app refuses a second vote (one person, one vote, enforced by a database constraint).

### Step 5: Check results and turnout

- As voter: open **View results** (visible immediately with the *Live* result mode).
- As admin: check turnout, download the **voted / not-voted** lists and **results CSV**, and review the **Audit log**.
- Use **Close voting now** to end the election early.

### Optional: test a voter-list (CSV) election

1. Create a second election and choose **Option 2: Vote by Voter List (CSV)**.
2. Upload a CSV with this format (header row optional):
   ```csv
   email,name,roll_number
   alice@gmail.com,Alice,CS001
   bob@gmail.com,Bob,CS002
   ```
3. Each row's email domain must be one of that election's allowed domains, otherwise it is counted as **invalid**. (The bundled sample `voters.csv` uses `yourcollege.edu`, so it only imports if that domain is allowed for the election.)
4. Only emails on the list can sign in and vote. Emails not on the list see *"This email is not registered for any election."*

---

## 7. Receiving real OTP emails (optional)

Skip this unless you want real emails during local testing.

1. In your Google account, enable **2-Step Verification**.
2. Create an **App password** at <https://myaccount.google.com/apppasswords>. Copy the 16-character password (no spaces).
3. Add these lines to `.env` (keep `COLLEGE_DOMAIN` and `ADMIN_EMAILS` too):
   ```properties
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USER=yourname@gmail.com
   SMTP_PASSWORD=your16charapppassword
   ```
4. Restart the app. The log should now say `Email is configured: sign-in codes will be emailed through smtp.gmail.com ...`. If it still says `NOT configured`, the `.env` file is not in the folder you started from.
5. Request a code; check your inbox and the spam folder.

With SMTP configured, codes are **no longer printed** in the terminal.

---

## 8. Run with Docker Compose (optional)

This runs the app with a **MySQL** database in containers, using the production profile. You do not need Java or Maven installed for this route, only Docker.

> **Heads-up:** the production profile **does not print codes to the console**. Email must be configured, or sign-in fails with *"Email service is not configured on the server."* For quick local testing, prefer [section 4](#4-run-it-fastest-path).

1. Create `.env` in `online-voting-system/` (copy `.env.example` and fill in **real** values):
   ```properties
   JWT_SECRET=<48+ random characters>
   COLLEGE_DOMAIN=gmail.com
   ADMIN_EMAILS=you@gmail.com
   DB_PASSWORD=pick-a-password
   DB_ROOT_PASSWORD=pick-another-password
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USER=yourname@gmail.com
   SMTP_PASSWORD=your16charapppassword
   ```
   Generate a secret with `openssl rand -base64 48` (on Windows, run it in **Git Bash**).
   Compose refuses to start if `JWT_SECRET` is missing.
2. Start everything:
   ```bash
   docker compose up --build -d
   ```
   The first build downloads images and dependencies and can take several minutes.
3. Open <http://localhost:8080>.
4. Useful commands:
   ```bash
   docker compose logs -f app     # watch the application log
   docker compose down            # stop (keeps database data)
   docker compose down -v         # stop AND delete the database volume
   ```

---

## 9. Run the automated tests

From the `online-voting-system/` folder:

```bash
mvn test
```

The project includes 18 tests (voting flow, eligibility, email utilities). They use an in-memory/test configuration, so they do not touch your local `./data` database. A successful run ends with `BUILD SUCCESS`.

To produce a runnable jar instead:
```bash
mvn clean package
java -jar target/online-voting-system-1.0.0.jar
```
(Set `COLLEGE_DOMAIN` and `ADMIN_EMAILS` the same way as in section 4, or keep your `.env` in the folder you run it from.)

---

## 10. Useful URLs, stopping, and resetting

| What | URL |
| --- | --- |
| Sign-in page | <http://localhost:8080/> |
| Voter page | <http://localhost:8080/vote.html> |
| Admin console | <http://localhost:8080/admin.html> |
| Swagger API docs | <http://localhost:8080/swagger-ui.html> |
| Public config (JSON) | <http://localhost:8080/api/public/config> |

**Stop:** `Ctrl + C` in the terminal that is running the app.

**Run on a different port** (if 8080 is busy):
```bash
# macOS / Linux
PORT=9090 mvn spring-boot:run
# Windows PowerShell
$env:PORT = "9090"; mvn spring-boot:run
```

**Reset all local data** (elections, votes, users): stop the app and delete the `data` folder inside `online-voting-system/`. A fresh database is created on the next start. This only applies to the local H2 setup.

---

## 11. Troubleshooting

| Symptom | Likely cause and fix |
| --- | --- |
| `'mvn' is not recognized` / `command not found: mvn` | Maven is not installed or not on PATH. Reinstall per section 2.2 and **open a new terminal**. |
| `no POM in this directory` | You are in the wrong folder. `cd` into the folder containing `pom.xml`. |
| `invalid target release: 17` or `release version 17 not supported` | Maven is using an older Java. Run `mvn -version`; set `JAVA_HOME` to JDK 17 and reopen the terminal. |
| Build errors mentioning Lombok on very new Java versions | Use JDK 17 (or 21). |
| `Port 8080 was already in use` | Another program uses 8080. Stop it or set `PORT=9090`. |
| `Could not resolve dependencies` / download failures | No internet, or a proxy/firewall is blocking Maven Central. Check your connection and retry. |
| *"The domain '@xyz' does not exist or has no active mail server."* | The DNS check failed. Use a real domain (like `gmail.com`) and make sure you are online. |
| *"Use your college email address (ending in @...)"* | The email's domain is not in `COLLEGE_DOMAIN` or in any election's allowed domains. Fix the setting, or create an election that allows it. Remember to restart after changing `.env`. |
| *"This email is not registered for any election."* | For a non-admin: there is no open election for that domain and the email is not on any voter list. Create an election (Option 1) or upload the voter CSV. |
| No code in the terminal | Scroll up/down and look for `OTP for <email> is ######` (it is a `WARN` line). Make sure SMTP is **not** configured (if it is, the code is emailed instead). |
| *"Failed to deliver email to ..."* | SMTP is configured but wrong. Remove the `SMTP_*` lines from `.env` for console codes, or fix the credentials (Gmail needs an App password, and `MAIL_FROM` must equal `SMTP_USER`). |
| *"Too many requests"* (HTTP 429) | Rate limit: 3 codes per email per 10 minutes. Wait and retry. |
| Code rejected | Codes expire after 5 minutes and allow 5 attempts. Use **Send a new code**; only the newest code works. |
| Admin email cannot reach the admin console | The address must be listed in `ADMIN_EMAILS` (comma-separated) **and the app restarted**. |
| Startup fails with *"JWT secret missing or too short"* | You are running the `prod` profile (or Docker) without a 32+ character `JWT_SECRET`. |
| Docker: `Set JWT_SECRET (32+ characters) in .env` | Add `JWT_SECRET` to the `.env` next to `docker-compose.yml`. |
| Voting not possible / "Voting has not started yet" | The election's start time is in the future. Edit it (only allowed before it starts) or create a new one with an earlier start. |
| Cannot edit candidates or election details | The ballot locks after the first vote, and details can only be edited before voting opens. |

---

## 12. Put the project on GitHub

### 12.1 Before you commit: protect secrets

- **Never commit `.env`.** It holds your SMTP app password and JWT secret. The project's `.gitignore` (inside `online-voting-system/`) already ignores `.env`, `target/`, `data/`, `*.log`, and IDE folders. Keep your `.env` **inside `online-voting-system/`** so that rule applies. A `.env` created in the outer folder would *not* be covered.
- **Do not commit real voter lists.** The bundled `voters.csv` is just a sample with placeholder emails. Keep real student data out of a public repository.
- If you ever commit a secret by accident, treat it as leaked: revoke the Gmail App password and generate a new `JWT_SECRET`, then remove it from the repository history. Deleting the file in a later commit is not enough.

### 12.2 Create the empty repository on GitHub

1. Sign in at <https://github.com> and click **New repository** (the **+** menu, top right).
2. Repository name: `online_voting_system` (or any name you like).
3. Choose **Public** or **Private**.
4. **Leave all "Initialize this repository with..." boxes unchecked** (no README, no .gitignore, no license). Your project already has these, and adding them on GitHub causes merge conflicts on the first push.
5. Click **Create repository**. Keep the page open; you need its URL, which looks like `https://github.com/YOUR-USERNAME/online_voting_system.git`.

### 12.3 Add this guide to the repository

Copy `LOCAL_SETUP.md` into the **repository root**, next to the existing `README.md` (the folder that contains the `online-voting-system/` folder, not inside it).

Optional: add a link near the top of `README.md` so visitors find it:
```markdown
📘 **New here? See the [full local setup guide](LOCAL_SETUP.md).**
```

### 12.4 Initialise Git and make the first commit

Open a terminal in the **repository root** (the folder containing `README.md`, `LOCAL_SETUP.md` and `online-voting-system/`):

```bash
git init -b main
git add .
git status
```

Read the `git status` list carefully before continuing. You should see source files, `pom.xml`, `README.md`, and so on. You should **not** see `.env`, `target/`, or `data/`. If you do, stop and fix your `.gitignore` first.

Then commit:
```bash
git commit -m "Initial commit: online voting system with local setup guide"
```

### 12.5 Connect to GitHub and push

Replace `YOUR-USERNAME` with your GitHub username:
```bash
git remote add origin https://github.com/YOUR-USERNAME/online_voting_system.git
git push -u origin main
```

**Authentication:** GitHub no longer accepts your account password on the command line. Pick one:

- **GitHub CLI (easiest):** install from <https://cli.github.com>, run `gh auth login`, follow the prompts, then run the `git push` again.
- **Personal access token:** on GitHub go to *Settings, Developer settings, Personal access tokens* and create a token with the `repo` scope. When Git asks for a password, paste the token. (Git Credential Manager on Windows/macOS will remember it.)
- **SSH:** add an SSH key to your GitHub account and use the `git@github.com:YOUR-USERNAME/online_voting_system.git` URL instead.

Refresh the repository page on GitHub. Your code, the README, and this guide should now be there.

### 12.6 Make later changes

```bash
git add .
git status                        # always check what is being added
git commit -m "Describe your change"
git push
```

### 12.7 Optional extras for a polished repo

- Add a `LICENSE` file (GitHub: *Add file, Create new file, type `LICENSE`, "Choose a license template"*).
- Update the clone URL inside `README.md` so it points to **your** repository.
- Add a short repository description and topics (for example `spring-boot`, `java`, `online-voting`, `jwt`) using the gear icon on the repository page.

---

## 13. Cloning it on another computer

After installing Java 17, Maven and Git (section 2):

```bash
git clone https://github.com/YOUR-USERNAME/online_voting_system.git
cd online_voting_system/online-voting-system
```

Then create your own `.env` (section 5; it is not in the repository by design) and start the app:

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>.

---

*Need to deploy for a real election? See [`online-voting-system/DEPLOY.md`](online-voting-system/DEPLOY.md) for hosting options (Render, Google Cloud Run, or your own server) and an election-day checklist.*
