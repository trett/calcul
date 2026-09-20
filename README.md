# CalTrack AI

CalTrack AI is an intelligent calorie and daily weight tracking application. It helps you monitor your nutrition, stay within your daily calorie goals, and track your body weight over time.

### Key Features
- **AI-Powered Meal Logging:** Describe what you ate in plain text or take a photo of your meal. The AI automatically identifies the food items, portions, estimated calories, and macronutrients (protein, carbs, fat).
- **Interactive Review:** Review and edit the AI-generated breakdown before saving to your daily log.
- **Daily Calorie Budget:** Track your total calories against your daily target with real-time remaining budget calculations.
- **Daily Weight Tracking:** Record daily weigh-ins with metric (kg) or imperial (lbs) units, and monitor your 30-day weight trends.
- **Google Sign-In:** Secure authentication using your Google account.
- **Clean Responsive Interface:** Fast, modern interface with dark and light mode support.

---

## Production Setup Guide

### 1. Prerequisites
- A Linux server (Ubuntu 22.04 LTS or Debian 12 recommended)
- **Docker** (version 24+) and **Docker Compose** (v2+) installed
- A registered domain name with an `A` record pointing to your server's public IP address (e.g., `caltrack.example.com`)
- A **Google Gemini API Key** from [Google AI Studio](https://aistudio.google.com/)
- A **Google OAuth2 Client ID and Secret** from [Google Cloud Console](https://console.cloud.google.com/apis/credentials)

---

### 2. Configure Google OAuth2
In the [Google Cloud Console](https://console.cloud.google.com/):
1. Go to **APIs & Services > Credentials**.
2. Click **Create Credentials > OAuth client ID** and select **Web application**.
3. Under **Authorized redirect URIs**, add your production callback URL:
   ```
   https://caltrack.example.com/api/auth/callback
   ```
4. Save your **Client ID** and **Client Secret**.

---

### 3. Server Setup

#### Clone the Repository
```bash
git clone <your-repository-url> /opt/caltrack
cd /opt/caltrack
```

#### Create the Production Environment File (`.env`)
Create a `.env` file in the project directory:

```bash
# ==============================================================================
# CalTrack AI Production Configuration
# ==============================================================================

# Server Port
PORT=8080

# PostgreSQL Database Credentials
POSTGRES_DB=calcul
POSTGRES_USER=calcul_user
POSTGRES_PASSWORD=choose_a_strong_database_password_here
POSTGRES_PORT=5432

# Application Database Connection
JDBC_URL=jdbc:postgresql://postgres:5432/calcul
DB_USER=calcul_user
DB_PASSWORD=choose_a_strong_database_password_here

# Google Gemini API Key
GEMINI_API_KEY=your_gemini_api_key_here

# Google OAuth2 Credentials
GOOGLE_CLIENT_ID=your_client_id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your_google_client_secret
GOOGLE_REDIRECT_URI=https://caltrack.example.com/api/auth/callback

# Session Security (32+ character random secret for signing session cookies)
# Generate one with: openssl rand -hex 32
SESSION_SECRET=your_random_32_character_session_signing_secret
```

---

### 4. Build and Start the Application

Build the application image using sbt and start the stack:

```bash
# Build the Docker image via sbt
sbt buildImage

# Start the application and database containers in detached mode
docker compose up -d
```

The application will:
1. Start PostgreSQL and automatically initialize the database schema from `schema.sql`.
2. Wait for PostgreSQL to become healthy.
3. Launch the CalTrack application container.

#### Verify the Deployment
Check container status:
```bash
docker compose ps
```

Check application logs:
```bash
docker compose logs -f app
```

Test the healthcheck endpoint:
```bash
curl -f http://localhost:8080/api/health
# Response: {"status":"ok"}
```

---

### 5. SSL & Domain Configuration (Reverse Proxy)

Do not expose the application port directly to the internet. Use a reverse proxy to terminate SSL.

#### Option A: Caddy (Recommended - Automatic HTTPS)
Install Caddy and edit `/etc/caddy/Caddyfile`:

```caddy
caltrack.example.com {
    reverse_proxy localhost:8080

    encode zstd gzip

    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
    }
}
```

Reload Caddy:
```bash
sudo systemctl reload caddy
```

#### Option B: NGINX + Let's Encrypt Certbot
Create `/etc/nginx/sites-available/caltrack`:

```nginx
server {
    listen 80;
    server_name caltrack.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name caltrack.example.com;

    ssl_certificate /etc/letsencrypt/live/caltrack.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/caltrack.example.com/privkey.pem;

    client_max_body_size 20M; # Required for meal photo uploads

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the site and reload NGINX:
```bash
sudo ln -s /etc/nginx/sites-available/caltrack /etc/nginx/sites-enabled/
sudo systemctl reload nginx
```

---

### 6. Database Backups & Maintenance

#### Create a Backup
```bash
docker compose exec -T postgres pg_dump -U calcul_user -F c -b -v -f /tmp/backup.dump calcul
docker compose cp postgres:/tmp/backup.dump ./caltrack_backup_$(date +%Y%m%d).dump
```

#### Restore from Backup
```bash
docker compose cp ./caltrack_backup.dump postgres:/tmp/restore.dump
docker compose exec -T postgres pg_restore -U calcul_user -d calcul --clean --if-exists /tmp/restore.dump
```

---

### 7. Upgrades & Updates

To update to a new version:
```bash
cd /opt/caltrack
git pull
sbt buildImage
docker compose up -d
```

---

## Environment Variables Reference

| Variable | Required | Description |
| :--- | :---: | :--- |
| `PORT` | No | Application port (default: `8080`). |
| `JDBC_URL` | **Yes** | PostgreSQL connection URL (e.g., `jdbc:postgresql://postgres:5432/calcul`). |
| `DB_USER` | **Yes** | PostgreSQL database user. |
| `DB_PASSWORD` | **Yes** | PostgreSQL database password. |
| `GEMINI_API_KEY` | **Yes** | Google Gemini API Key for meal analysis. |
| `GOOGLE_CLIENT_ID` | **Yes** | Google OAuth2 Web Client ID. |
| `GOOGLE_CLIENT_SECRET` | **Yes** | Google OAuth2 Web Client Secret. |
| `GOOGLE_REDIRECT_URI` | **Yes** | OAuth redirect URL (`https://yourdomain.com/api/auth/callback`). |
| `SESSION_SECRET` | **Yes** | Random 32+ character key for signing session authentication cookies. |
