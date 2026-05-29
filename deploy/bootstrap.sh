#!/usr/bin/env bash
# Bootstrap a fresh Ubuntu 24.04 EC2 instance to run finTrackAPI.
# Installs Postgres + Java + Nginx, clones the repo, builds the jar, creates a
# local DB + app user, and writes /etc/fintrack-api.env with generated secrets.
#
# Run as root once after SSH'ing into the instance:
#   sudo REPO_URL=https://github.com/<your-user>/finTrackAPI.git bash bootstrap.sh
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/CHANGE_ME/finTrackAPI.git}"
APP_DIR="/opt/fintrack-api"
APP_USER="fintrack"
DB_NAME="fintrack"
DB_USER="fintrack_app"
ENV_FILE="/etc/fintrack-api.env"

if [[ "$REPO_URL" == *CHANGE_ME* ]]; then
  echo "ERROR: set REPO_URL=https://github.com/your-user/finTrackAPI.git" >&2
  exit 1
fi

echo "==> Updating apt cache"
apt-get update -y

echo "==> Installing OpenJDK 17, Postgres, Nginx, git"
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk-headless \
  postgresql postgresql-contrib \
  nginx \
  git unzip ca-certificates curl

echo "==> Ensuring Postgres is running"
systemctl enable --now postgresql

echo "==> Creating service user '$APP_USER'"
if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "$APP_DIR" --shell /bin/bash "$APP_USER"
fi
install -d -o "$APP_USER" -g "$APP_USER" "$APP_DIR"
install -d -o "$APP_USER" -g "$APP_USER" "$APP_DIR/logs"

echo "==> Cloning / updating repository"
sudo -u "$APP_USER" bash -lc "
  set -e
  cd '$APP_DIR'
  if [ ! -d .git ]; then
    git clone '$REPO_URL' .
  else
    git fetch --all --prune
    git reset --hard origin/main
  fi
  chmod +x mvnw
  ./mvnw -DskipTests clean package
"

echo "==> Linking built jar to $APP_DIR/app.jar"
JAR_PATH=$(find "$APP_DIR/target" -maxdepth 1 -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)
if [[ -z "$JAR_PATH" ]]; then
  echo "ERROR: no jar found in $APP_DIR/target" >&2
  exit 1
fi
ln -sfn "$JAR_PATH" "$APP_DIR/app.jar"

echo "==> Creating Postgres database + app user (idempotent)"
DB_EXISTS=$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")
USER_EXISTS=$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'")

if [[ -f "$ENV_FILE" ]] && grep -q '^PGPASSWORD=' "$ENV_FILE"; then
  DB_PASSWORD=$(grep '^PGPASSWORD=' "$ENV_FILE" | cut -d= -f2-)
  echo "    reusing existing DB password from $ENV_FILE"
else
  DB_PASSWORD=$(openssl rand -base64 32 | tr -d '=+/\n' | cut -c1-32)
fi

if [[ -z "$USER_EXISTS" ]]; then
  sudo -u postgres psql -c "CREATE USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
else
  sudo -u postgres psql -c "ALTER USER $DB_USER WITH PASSWORD '$DB_PASSWORD';"
fi

if [[ -z "$DB_EXISTS" ]]; then
  sudo -u postgres psql -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"
fi
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;"
sudo -u postgres psql -d "$DB_NAME" -c "GRANT ALL ON SCHEMA public TO $DB_USER;"

echo "==> Installing systemd unit"
install -m 644 "$APP_DIR/deploy/fintrack-api.service" /etc/systemd/system/fintrack-api.service

if [[ ! -f "$ENV_FILE" ]]; then
  echo "==> Writing $ENV_FILE with generated DB password + JWT secret"
  JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
  umask 027
  cat > "$ENV_FILE" <<EOF
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

# ===== Local Postgres (co-located on this EC2) =====
DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/$DB_NAME
PGUSER=$DB_USER
PGPASSWORD=$DB_PASSWORD

# ===== Schema bootstrap overrides =====
# Single-box staging has no Flyway migrations checked in. Let JPA create the
# schema on first start and skip Flyway entirely. For real production: generate
# a Flyway baseline and remove these two lines.
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_FLYWAY_ENABLED=false

# ===== JWT =====
JWT_SECRET=$JWT_SECRET
JWT_EXPIRATION=86400000

# ===== Frontend URL (used in password reset emails) =====
# Set to your Flutter web origin once deployed; for now the EC2 public URL.
FRONTEND_URL=http://localhost:5000

# ===== Mail (SMTP) — fill these in before starting the service =====
# For Gmail: create an App Password at https://myaccount.google.com/apppasswords
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=CHANGE_ME@gmail.com
MAIL_PASSWORD=CHANGE_ME_app_password
MAIL_FROM=noreply@example.com
EOF
  chown root:"$APP_USER" "$ENV_FILE"
  chmod 640 "$ENV_FILE"
else
  echo "==> $ENV_FILE already exists, leaving it alone"
fi

systemctl daemon-reload
systemctl enable fintrack-api

echo ""
echo "==> Bootstrap complete."
echo ""
echo "Next steps:"
echo "  1. Edit $ENV_FILE and fill in MAIL_* credentials"
echo "       sudo nano $ENV_FILE"
echo "  2. Start the service and tail logs:"
echo "       sudo systemctl start fintrack-api"
echo "       sudo journalctl -u fintrack-api -f"
echo "  3. Install Nginx site (reverse proxy on port 80):"
echo "       sudo cp $APP_DIR/deploy/nginx.conf /etc/nginx/sites-available/fintrack-api"
echo "       sudo ln -sf /etc/nginx/sites-available/fintrack-api /etc/nginx/sites-enabled/"
echo "       sudo rm -f /etc/nginx/sites-enabled/default"
echo "       sudo nginx -t && sudo systemctl reload nginx"
echo "  4. Verify from your laptop:"
echo "       curl http://<elastic-ip>/actuator/health"
