
---

## 12. Live Deployment (October 2026)

Fully self-hosted on custom domain.

| Layer | URL | Hosting |
|-------|-----|---------|
| Frontend | https://randsys.indevs.in | Cloudflare Worker |
| Frontend WWW | https://www.randsys.indevs.in | Cloudflare Worker |
| Backend API | https://api.randsys.indevs.in | Cloudflare Tunnel to GCP VM |
| Database | (private) localhost:5432 | GCP VM |
| VM | rand-water-vm (34.35.142.209) | GCP africa-south1-a |
| DNS | randsys.indevs.in | Cloudflare |
| Domain registrar | Stackryze (.indevs.in subdomain) | - |

No Render. No Neon. No external quota limits.

### Infrastructure

Browser to Cloudflare Edge:
- randsys.indevs.in routes to Cloudflare Worker (frontend)
- api.randsys.indevs.in routes to Cloudflare Tunnel
- Cloudflare Tunnel to GCP VM localhost:8080 (Spring Boot)
- Backend to localhost:5432 (Postgres)

### VM services (systemd)

- postgresql - port 5432 localhost only - logs at /var/log/postgresql/
- randwater - port 8080 - logs at /home/dee_dyondzani/backend.log
- cloudflared - outbound QUIC - logs via journalctl -u cloudflared

### Scheduled tasks

- Daily 02:00 - DB backup (gzip, 7-day retention) - /home/dee_dyondzani/backup_db.sh
- Daily 04:00 - DB growth snapshot - DbGrowthService.java @Scheduled
- Sunday 03:00 - Audit log cleanup (1-year retention) - /home/dee_dyondzani/cleanup_audit.sh

### Tunnel details

- Tunnel ID: eec35fa5-4b29-4ac2-bca9-13521ecd40fd
- Config: /etc/cloudflared/config.yml
- Credentials: /etc/cloudflared/eec35fa5-*.json

### Timezone

VM runs Africa/Johannesburg (SAST, UTC+2). All timestamps stored in SAST.

### Security

- Passwords: BCrypt with policy (8+ chars, upper, lower, digit, special)
- JWT: HS384, 24-hour expiry
- Postgres: SSL for external, localhost for backend
- Firewall: only 22 (SSH) and 8080 (tunnel) exposed; DB not publicly reachable
- Domain: HTTPS via Cloudflare (auto SSL)
