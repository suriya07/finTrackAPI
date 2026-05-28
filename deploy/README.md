# Pre-launch staging deployment — AWS EC2 + RDS (ap-south-1)

Single-instance staging deploy of finTrackAPI to AWS. Everything sized for the 12-month free tier.

```
Android  ── HTTPS ──>  api.example.com  ──>  Nginx (EC2 t3.micro)  ──>  Spring Boot :8080
                                                                              │
                                                                              ▼
                                                                       RDS Postgres (db.t3.micro, private)
```

---

## 0. Prereqs

- AWS account, signed-in to the **ap-south-1 (Mumbai)** region in the console.
- A domain you control (or buy one first — Namecheap / Route 53 / Cloudflare Registrar).
- `ssh` and `git` installed locally.

---

## 1. RDS Postgres (db.t3.micro, free tier)

**Console → RDS → Create database:**

| Setting | Value |
|---|---|
| Engine | PostgreSQL (16.x) |
| Templates | Free tier |
| DB instance identifier | `fintrack-db` |
| Master username | `fintrack_admin` |
| Master password | *(save in your password manager)* |
| Instance class | `db.t3.micro` |
| Storage | 20 GB gp3, **disable** autoscaling for staging |
| Multi-AZ | No |
| VPC | default VPC |
| Public access | **No** (only EC2 reaches it) |
| VPC security group | create new: `fintrack-db-sg` |
| Initial database name | `fintrack` |
| Backup retention | 1 day |

After creation, note the **Endpoint** — looks like `fintrack-db.xxxxxx.ap-south-1.rds.amazonaws.com`.

### Create an app-level DB user (don't use the master)

Connect from your laptop temporarily (set RDS to "Publicly accessible: Yes" briefly, or do it from the EC2 after step 2):

```sql
CREATE USER fintrack_app WITH PASSWORD 'GENERATE_A_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE fintrack TO fintrack_app;
\c fintrack
GRANT ALL ON SCHEMA public TO fintrack_app;
```

Then flip "Publicly accessible" back to No.

---

## 2. EC2 (t3.micro Ubuntu 24.04, free tier)

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
| HTTP | 80 | 0.0.0.0/0, ::/0 | certbot HTTP-01 challenge + redirect |
| HTTPS | 443 | 0.0.0.0/0, ::/0 | API traffic |

### Allow EC2 → RDS

Edit `fintrack-db-sg` inbound:

| Type | Port | Source |
|---|---|---|
| PostgreSQL | 5432 | `fintrack-api-sg` (security group, not IP) |

### Elastic IP

**EC2 → Elastic IPs → Allocate → Associate with `fintrack-api`.** This is the IP you'll point DNS at — it doesn't change across reboots.

---

## 3. Bootstrap the server

Push `finTrackAPI` to GitHub first (if private, generate a deploy key or use HTTPS + a PAT).

SSH in:

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

This installs Java 17, Nginx, certbot, clones the repo to `/opt/fintrack-api`, builds the jar, and installs the systemd unit (stopped).

---

## 4. Configure env vars

```bash
sudo nano /etc/fintrack-api.env
```

Fill in `DATABASE_URL`, `PGUSER`, `PGPASSWORD`, `JWT_SECRET`, mail creds. Generate JWT secret:

```bash
openssl rand -base64 64 | tr -d '\n'
```

Start the service:

```bash
sudo systemctl start fintrack-api
sudo journalctl -u fintrack-api -f   # watch logs; Ctrl-C when you see "Started"
```

Sanity check from the EC2 itself:

```bash
curl -i http://127.0.0.1:8080/actuator/health
```

---

## 5. Nginx + DNS + HTTPS

```bash
sudo cp /opt/fintrack-api/deploy/nginx.conf /etc/nginx/sites-available/fintrack-api
# Edit the server_name line to your real domain:
sudo sed -i 's/api.example.com/api.yourdomain.com/g' /etc/nginx/sites-available/fintrack-api
sudo ln -sf /etc/nginx/sites-available/fintrack-api /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

**Point DNS** at the Elastic IP — at your registrar:

| Type | Name | Value | TTL |
|---|---|---|---|
| A | `api` (or whatever subdomain) | `<elastic-ip>` | 300 |

Wait for propagation (`dig api.yourdomain.com` should return the EIP). Then issue the cert:

```bash
sudo certbot --nginx -d api.yourdomain.com --agree-tos -m you@yourdomain.com --redirect
```

certbot rewrites `/etc/nginx/sites-available/fintrack-api` to add the TLS server block + auto-renew timer.

---

## 6. Update the Flutter app

In `lib/services/api_client.dart`:

```dart
static const String _productionBaseUrl = 'https://api.yourdomain.com';
```

(See the Flutter repo's `api_client.dart` line 18.)

For Android **debug builds** to also hit staging instead of LAN IP, temporarily change the `defaultTargetPlatform == TargetPlatform.android` branch to return the same URL.

---

## Updating the API later

```bash
ssh -i fintrack-api-key.pem ubuntu@<elastic-ip>
sudo -u fintrack bash -lc 'cd /opt/fintrack-api && git pull && ./mvnw -DskipTests clean package'
sudo systemctl restart fintrack-api
sudo journalctl -u fintrack-api -f
```

You can wrap that into a `deploy/update.sh` if you redeploy often.

---

## Costs (after free tier expires, ~month 13)

| Resource | Approx monthly |
|---|---|
| EC2 t3.micro on-demand (ap-south-1) | ~$8 |
| EBS 30GB gp3 | ~$3 |
| RDS db.t3.micro | ~$13 |
| RDS storage 20GB | ~$2 |
| Elastic IP (while associated) | $0 |
| Data transfer (light staging) | ~$1 |
| **Total** | **~$27/mo** |

Cheaper if you go down to db.t4g.nano on RDS or move to Lightsail when free tier runs out.
