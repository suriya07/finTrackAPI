# Single-box staging deployment — AWS EC2 (ap-south-1)

One `t3.micro` Ubuntu instance running Spring Boot + Postgres + Nginx. No RDS, no domain, no TLS yet — accessed by Elastic IP over HTTP. Sized for the 12-month AWS free tier.

```
Android  ── HTTP ──>  http://<elastic-ip>  ──>  Nginx :80  ──>  Spring Boot :8080  ──>  Postgres :5432 (localhost)
```

When you eventually buy a domain, follow the **[Adding a domain + HTTPS](#adding-a-domain--https-later)** section at the bottom — no other changes needed.

---

## 0. Prereqs

- AWS account, signed-in to the **ap-south-1 (Mumbai)** region in the console.
- `ssh` and `git` installed locally.
- `finTrackAPI` pushed to GitHub (public, or with a deploy key / PAT if private).

---

## 1. Launch EC2 (t3.micro Ubuntu 24.04, free tier)

**Console → EC2 → Launch instance:**

| Setting | Value |
|---|---|
| Name | `fintrack-api` |
| AMI | Ubuntu Server 24.04 LTS (free tier eligible) |
| Instance type | `t3.micro` |
| Key pair | create new, download `.pem` |
| VPC / subnet | default VPC, any public subnet |
| Auto-assign public IP | Enable |
| Security group | create new: `fintrack-api-sg` |
| Storage | 30 GB gp3 (free tier ceiling) |

### `fintrack-api-sg` inbound rules

| Type | Port | Source | Why |
|---|---|---|---|
| SSH | 22 | My IP | SSH access |
| HTTP | 80 | 0.0.0.0/0, ::/0 | API traffic (and certbot HTTP-01 later) |

Postgres stays on `127.0.0.1` — no inbound rule needed.

### Elastic IP

**EC2 → Elastic IPs → Allocate → Associate with `fintrack-api`.** Free while attached, and survives reboots.

---

## 2. Bootstrap the server

```bash
chmod 400 fintrack-api-key.pem
ssh -i fintrack-api-key.pem ubuntu@<elastic-ip>
```

Run the bootstrap script:

```bash
# On the EC2 instance:
curl -fsSL https://raw.githubusercontent.com/<your-user>/finTrackAPI/main/deploy/bootstrap.sh -o bootstrap.sh
sudo REPO_URL=https://github.com/<your-user>/finTrackAPI.git bash bootstrap.sh
```

This:
- Installs OpenJDK 17, Postgres, Nginx, git
- Creates a `fintrack` Linux service user
- Clones the repo to `/opt/fintrack-api` and builds the jar
- Creates Postgres database `fintrack` + user `fintrack_app` with a random password
- Writes `/etc/fintrack-api.env` with that password and a fresh JWT secret already filled in
- Installs the `fintrack-api` systemd unit (stopped, awaiting mail creds)

---

## 3. Add mail credentials

Only one thing left in the env file — SMTP:

```bash
sudo nano /etc/fintrack-api.env
```

Set:
```
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=<gmail-app-password>
MAIL_FROM=you@gmail.com
```

> For Gmail, generate an App Password at <https://myaccount.google.com/apppasswords> — your normal password won't work with SMTP.

---

## 4. Start the service

```bash
sudo systemctl start fintrack-api
sudo journalctl -u fintrack-api -f   # Ctrl-C once you see "Started Finance Manager"
```

Sanity check from the EC2 itself:

```bash
curl -i http://127.0.0.1:8080/actuator/health
```

You should see `{"status":"UP"}`.

> **Schema note:** the env file sets `SPRING_JPA_HIBERNATE_DDL_AUTO=update` and `SPRING_FLYWAY_ENABLED=false`. JPA will auto-create tables on first start. Fine for staging — for real production, generate a Flyway baseline and drop those two overrides.

---

## 5. Put Nginx in front

```bash
sudo cp /opt/fintrack-api/deploy/nginx.conf /etc/nginx/sites-available/fintrack-api
sudo ln -sf /etc/nginx/sites-available/fintrack-api /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

From your laptop:

```bash
curl http://<elastic-ip>/actuator/health
curl http://<elastic-ip>/healthz
```

The API is now reachable at `http://<elastic-ip>`.

---

## 6. Point the Flutter app at it

In `lib/services/api_client.dart`, set the base URL to `http://<elastic-ip>` (or a `_productionBaseUrl` constant) for builds you want hitting staging.

**Android cleartext gotcha:** Android 9+ blocks cleartext HTTP by default. Until you add HTTPS, you'll need either:
- `android:usesCleartextTraffic="true"` on `<application>` in `android/app/src/main/AndroidManifest.xml`, **or**
- a `network_security_config.xml` that whitelists your EC2 IP.

iOS is similar via `NSAppTransportSecurity` in `Info.plist`. Both are temporary — remove once you're on HTTPS.

---

## Updating the API later

```bash
ssh -i fintrack-api-key.pem ubuntu@<elastic-ip>
sudo -u fintrack bash -lc 'cd /opt/fintrack-api && git pull && ./mvnw -DskipTests clean package'
sudo systemctl restart fintrack-api
sudo journalctl -u fintrack-api -f
```

Wrap into `deploy/update.sh` if you redeploy often.

---

## Adding a domain + HTTPS later

When you have a domain:

1. **DNS:** add an `A` record `api.yourdomain.com → <elastic-ip>` (TTL 300). Wait for `dig api.yourdomain.com` to return the EIP.

2. **Edit nginx.conf** — change `server_name _;` to `server_name api.yourdomain.com;`, reload nginx.

3. **Open 443** in `fintrack-api-sg` (HTTPS, 0.0.0.0/0).

4. **Install certbot and issue the cert:**
   ```bash
   sudo apt-get install -y certbot python3-certbot-nginx
   sudo certbot --nginx -d api.yourdomain.com --agree-tos -m you@yourdomain.com --redirect
   ```

5. **Update Flutter app** to `https://api.yourdomain.com`. Drop the cleartext-traffic workarounds.

---

## Backups (Postgres, on-box)

The DB lives on the same EBS volume as everything else. EBS snapshots cover the whole box, but a logical `pg_dump` is nice to have. Cron a daily dump:

```bash
sudo crontab -e
# Add:
0 2 * * * sudo -u postgres pg_dump fintrack | gzip > /var/backups/fintrack-$(date +\%F).sql.gz && find /var/backups -name 'fintrack-*.sql.gz' -mtime +7 -delete
```

---

## Costs (after free tier expires, ~month 13)

| Resource | Approx monthly |
|---|---|
| EC2 t3.micro on-demand (ap-south-1) | ~$8 |
| EBS 30GB gp3 | ~$3 |
| Elastic IP (while associated) | $0 |
| Data transfer (light staging) | ~$1 |
| **Total** | **~$12/mo** |

Roughly half the RDS-backed version because Postgres is co-located. Trade-off: no automated backups, no Multi-AZ, manual upgrades.
