#!/usr/bin/env bash
# Bootstrap a fresh Ubuntu 24.04 EC2 instance to run finTrackAPI.
# Run as root once after SSH'ing into the instance:
#   sudo REPO_URL=https://github.com/<your-user>/finTrackAPI.git bash bootstrap.sh
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/CHANGE_ME/finTrackAPI.git}"
APP_DIR="/opt/fintrack-api"
APP_USER="fintrack"

if [[ "$REPO_URL" == *CHANGE_ME* ]]; then
  echo "ERROR: set REPO_URL=https://github.com/your-user/finTrackAPI.git" >&2
  exit 1
fi

echo "==> Updating apt cache"
apt-get update -y

echo "==> Installing OpenJDK 17, Nginx, certbot, git"
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jdk-headless \
  nginx \
  certbot python3-certbot-nginx \
  git unzip ca-certificates curl

echo "==> Creating service user '$APP_USER'"
if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "$APP_DIR" --shell /bin/bash "$APP_USER"
fi
install -d -o "$APP_USER" -g "$APP_USER" "$APP_DIR"

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

echo "==> Installing systemd unit"
install -m 644 "$APP_DIR/deploy/fintrack-api.service" /etc/systemd/system/fintrack-api.service

if [[ ! -f /etc/fintrack-api.env ]]; then
  echo "==> Creating /etc/fintrack-api.env from example (edit it before starting!)"
  install -m 640 "$APP_DIR/deploy/fintrack-api.env.example" /etc/fintrack-api.env
  chown root:"$APP_USER" /etc/fintrack-api.env
fi

systemctl daemon-reload
systemctl enable fintrack-api

echo ""
echo "==> Bootstrap complete."
echo "Next steps:"
echo "  1. Edit /etc/fintrack-api.env with RDS endpoint, JWT_SECRET, mail creds"
echo "  2. systemctl start fintrack-api && journalctl -u fintrack-api -f"
echo "  3. Install Nginx config:    cp $APP_DIR/deploy/nginx.conf /etc/nginx/sites-available/fintrack-api"
echo "  4. Enable site:             ln -sf /etc/nginx/sites-available/fintrack-api /etc/nginx/sites-enabled/"
echo "  5. Test + reload:           nginx -t && systemctl reload nginx"
echo "  6. Issue cert:              certbot --nginx -d api.yourdomain.com"
