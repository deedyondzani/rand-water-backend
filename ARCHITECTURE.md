# Rand Water Quality System — Full Architecture

Last updated: 2026-10-15
Maintainer: deedyondzani
Domain: randsys.indevs.in

================================================================
1. OVERVIEW
================================================================

A multi-plant SCADA-style water treatment application for
Palmiet, Eikenhof, and Zwartkopjes plants. Originally a Java
Swing desktop app (v5.1.2), migrated to a cloud-native web
stack (v6) with 100% self-hosted backend and database.

Fully self-hosted on:
- Frontend: Cloudflare Worker (free tier)
- Backend: Cloudflare Tunnel to GCP VM
- Database: GCP VM (Postgres 17, localhost only)
- Domain: randsys.indevs.in (via Stackryze + Cloudflare)

No external dependencies beyond GitHub (source) and Cloudflare
(edge + DNS). Zero monthly cost after November 2026 (GCP trial
expires; migrates to Oracle Cloud Always Free).

================================================================
2. ARCHITECTURE DIAGRAM
================================================================

    +------------------------------------------------------+
    |                  User (Browser)                      |
    +------------------------+-----------------------------+
                             |
                             | HTTPS
                             v
    +------------------------------------------------------+
    |              Cloudflare Edge (Free)                  |
    |                                                      |
    |  randsys.indevs.in         -> Worker (frontend)      |
    |  www.randsys.indevs.in     -> Worker (frontend)      |
    |  api.randsys.indevs.in     -> Cloudflare Tunnel      |
    +------------------------+-----------------------------+
                             |
                             | QUIC tunnel (outbound only)
                             | no inbound ports open
                             v
    +------------------------------------------------------+
    |              GCP VM: rand-water-vm                   |
    |              Static IP: 34.35.142.209                |
    |              Region: africa-south1-a                 |
    |              Timezone: Africa/Johannesburg (SAST)    |
    |                                                      |
    |  +------------------------------------------------+  |
    |  | cloudflared (systemd)                          |  |
    |  |   Tunnel ID: eec35fa5-4b29-4ac2-bca9-13521...  |  |
    |  |   Config: /etc/cloudflared/config.yml          |  |
    |  |   Ingress: api.randsys.indevs.in -> :8080      |  |
    |  +------------------------------------------------+  |
    |                                                      |
    |  +------------------------------------------------+  |
    |  | Spring Boot 3.3.4 (systemd: randwater)         |  |
    |  |   Port: 8080 (localhost)                       |  |
    |  |   Java 21                                      |  |
    |  |   Config: /etc/randwater.env                   |  |
    |  |   JAR: ~/rand-water-backend/target/*.jar       |  |
    |  +------------------------------------------------+  |
    |                                                      |
    |  +------------------------------------------------+  |
    |  | Postgres 17 (systemd: postgresql)              |  |
    |  |   Database: randwater                          |  |
    |  |   User: randwater_app                          |  |
    |  |   Listen: localhost only                       |  |
    |  |   TLS: enabled for external connections        |  |
    |  +------------------------------------------------+  |
    |                                                      |
    |  Cron jobs:                                          |
    |    Daily 02:00  -> backup_db.sh                     |
    |    Daily 04:00  -> DbGrowthService (Java @Scheduled)|
    |    Sun   03:00  -> cleanup_audit.sh                 |
    +------------------------------------------------------+

================================================================
3. PUBLIC URLS
================================================================

Frontend        https://randsys.indevs.in
Frontend (www)  https://www.randsys.indevs.in
Backend API     https://api.randsys.indevs.in
Health check    https://api.randsys.indevs.in/health
VM direct       http://34.35.142.209:8080 (firewall restricted)

================================================================
4. TECHNOLOGY STACK
================================================================

Frontend:
  React 19
  MUI 9 (Material-UI)
  react-router-dom 7
  axios 1
  jspdf + jspdf-autotable (PDF export)
  Hosted on Cloudflare Worker
  Built by CRA (create-react-app)
  Auto-deploy from GitHub main branch

Backend:
  Spring Boot 3.3.4
  Java 21
  Hibernate ORM 6.5
  Spring Security (JWT HS384)
  HikariCP connection pool
  Maven build
  Auto-deploy from GitHub via Render (suspended)
  Also runs directly on GCP VM (systemd)

Database:
  PostgreSQL 17
  Hosted on GCP VM
  Two access paths:
    - localhost:5432 (backend, no SSL)
    - 34.35.142.209:5432 (external, SSL required)

Infrastructure:
  GCP Compute Engine (e2-small)
  Cloudflare (edge + DNS + Tunnel + Workers)
  Cloudflare Tunnel (cloudflared, systemd)
  Static IP: 34.35.142.209

Domain:
  Registrar: Stackryze (randsys.indevs.in subdomain)
  DNS: Cloudflare
  Nameservers:
    ashton.ns.cloudflare.com
    rosemary.ns.cloudflare.com

================================================================
5. REPOSITORIES
================================================================

Backend:  github.com/deedyondzani/rand-water-backend
Frontend: github.com/deedyondzani/rand-water-frontend
Legacy:   github.com/deedyondzani/rand-water-quality-v6
          (Java Swing archive, git history sanitized)

Both active repos auto-deploy to their hosting platform on
push to main branch.

================================================================
6. ENVIRONMENT VARIABLES
================================================================

VM: /etc/randwater.env (chmod 640, root:dee_dyondzani)
  SPRING_DATASOURCE_URL
  SPRING_DATASOURCE_USERNAME
  SPRING_DATASOURCE_PASSWORD
  JWT_SECRET
  SPRING_JPA_HIBERNATE_DDL_AUTO=validate

Cloudflare Worker env vars:
  REACT_APP_API_URL=https://api.randsys.indevs.in

NOTE: Actual secret values are stored in a password manager,
NOT in this document or in the git repository.

================================================================
7. AUTHENTICATION & AUTHORIZATION
================================================================

Login:     POST /api/auth/login
Token:     JWT HS384, 24-hour expiry
Storage:   localStorage (rw_token, rw_user)
Header:    Authorization: Bearer <token>

Roles:
  admin       Full access to everything
  supervisor  Read-only across all plants
  operator    Full access to approved plants only

Password policy (enforced backend + frontend):
  - Minimum 8 characters
  - At least 1 uppercase letter (A-Z)
  - At least 1 lowercase letter (a-z)
  - At least 1 digit (0-9)
  - At least 1 special character

Passwords hashed with BCrypt (12 rounds).

Default admin (rotate before production):
  Username: admin
  Password: see password manager

================================================================
8. DATABASE SCHEMA
================================================================

12 tables:

users                  - accounts (BCrypt hashed passwords)
roles                  - admin, supervisor, operator
operator_plant_rights  - per-operator plant access
audit_log              - immutable event history (with notes)
db_growth_snapshot     - daily size/row metrics
backup_snapshot        - auto-backup snapshots (keep 500)
plant_lines            - line configurations per plant
pump_config            - pump specifications
pump_states            - live pump status + running time
cylinder_states        - chlorine cylinder states (Cl2)
tank_states            - ammonia tank states (NH3)
bulk_storage           - warehouse cylinder counts

Cylinder layout:
  Palmiet:      2 rooms x 10 slots = 20 cylinders
  Zwartkopjes:  2 rooms x 10 slots = 20 cylinders
  Eikenhof:     4 rooms x 10 slots = 40 cylinders
                Room 1 = East Bank  (Top Plant, 1-10)
                Room 2 = West Bank  (Top Plant, 11-20)
                Room 3 = North Bank (Bottom Plant, 1-10)
                Room 4 = South Bank (Bottom Plant, 11-20)

Bulk storage (per-plant keys):
  Palmiet
  Zwartkopjes
  Eikenhof-Top
  Eikenhof-Bottom

================================================================
9. DOSING CALCULATIONS
================================================================

Reference: Rand Water Zwartkopjes Chlorine Dosing Chart

Chlorine (Cl2):
  Diff = 1.9 - Free Cl2 (mg/L)
  Head = round(Diff x 10) / 10
  Flow = round(Flow / 10) x 10
  Cl2 Req (kg/h) = Flow x Head / 24  (HALF_UP to 2dp)

Ammonia (NH3):
  Lookup table by rounded flow (no formula)

Variance format:
  +X.XX Above Chart  (red/orange)
  -X.XX Below Chart  (blue)
  0.00 On Target     (green)

Plant design capacities:
  Palmiet:      2050 ML/d
  Eikenhof:     1200 ML/d
  Zwartkopjes:   700 ML/d
  Alarm at 105% of design.

================================================================
10. OPERATIONAL RUNBOOK
================================================================

SSH into VM:
  gcloud compute ssh rand-water-vm --zone=africa-south1-a

Check service status:
  sudo systemctl status postgresql
  sudo systemctl status randwater
  sudo systemctl status cloudflared

Restart backend:
  sudo systemctl restart randwater
  sleep 12
  curl -s http://localhost:8080/health

View backend logs:
  sudo journalctl -u randwater -n 100 --no-pager
  sudo tail -50 /home/dee_dyondzani/backend.log

Rebuild backend (JAR):
  cd ~/rand-water-backend
  export MAVEN_OPTS="-Xmx512m"
  nohup mvn clean package -DskipTests > /tmp/mvn.log 2>&1 &
  # Wait ~60s, then check /tmp/mvn.log
  sudo systemctl restart randwater

Database access:
  PGPASSWORD='<pw>' psql -h localhost -U randwater_app -d randwater

Test API from outside:
  curl -s https://api.randsys.indevs.in/health

Login test:
  curl -s -X POST https://api.randsys.indevs.in/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"<pw>"}'

================================================================
11. BACKUP & RETENTION
================================================================

Backup script:  /home/dee_dyondzani/backup_db.sh
Cron:           0 2 * * * (daily at 02:00 SAST)
Retention:      7 days
Location:       /home/dee_dyondzani/backups/*.sql.gz
Log:            /home/dee_dyondzani/backups/cron.log

Audit cleanup script:  /home/dee_dyondzani/cleanup_audit.sh
Cron:                  0 3 * * 0 (Sundays at 03:00 SAST)
Retention:             1 year
Log:                   /home/dee_dyondzani/backups/cleanup.log

DB growth snapshots:   Every day at 04:00 SAST
                       Auto-prunes >1 year old
                       Table: db_growth_snapshot

Backup snapshot retention:  Keep newest 500 in DB
                            Auto-pruned on each insert

Restore from backup:
  BACKUP=$(ls -t /home/dee_dyondzani/backups/*.sql.gz | head -1)
  PGPASSWORD='<pw>' psql -h localhost -U randwater_app -d randwater \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  gunzip -c "$BACKUP" | PGPASSWORD='<pw>' psql \
    -h localhost -U randwater_app -d randwater
  sudo systemctl restart randwater

================================================================
12. SECURITY POSTURE
================================================================

Network:
  - No inbound ports exposed publicly except 22 (SSH, GCP-managed)
  - All app traffic flows through Cloudflare Tunnel (outbound)
  - Postgres listens on localhost only
  - GCP firewall restricts 5432 to postgres-server tag (kept
    for emergency access, not used in normal operation)

Authentication:
  - BCrypt password hashing (12 rounds)
  - JWT HS384 with 24-hour expiry
  - Strong password policy enforced
  - Forced password change for new users

HTTPS:
  - All public endpoints HTTPS via Cloudflare
  - Automatic SSL cert management
  - Cloudflare Tunnel encrypts backend traffic

Audit:
  - All mutations logged with authenticated username
  - 1-year retention with weekly cleanup
  - Notes feature for operator/admin annotations

Secrets:
  - /etc/randwater.env (chmod 640, root:dee_dyondzani)
  - Cloudflare Worker env vars (encrypted at rest)
  - Password manager (offline backup)
  - NEVER in git, chat, or screenshots

Known follow-ups:
  - Rotate Neon password (project dormant)
  - Restrict GCP firewall 5432 to specific IPs (optional,
    tunnel handles production traffic)

================================================================
13. DOMAIN & DNS
================================================================

Domain: randsys.indevs.in
Registrar: Stackryze (free subdomain)
Nameservers (managed by Stackryze parent zone):
  ashton.ns.cloudflare.com
  rosemary.ns.cloudflare.com

Cloudflare Zone ID: (visible in Cloudflare dashboard)
Zone plan: Free

DNS Records (Cloudflare):
  randsys.indevs.in         AAAA  100::   proxied  (Worker)
  www.randsys.indevs.in     AAAA  100::   proxied  (Worker)
  api.randsys.indevs.in     CNAME -> eec35fa5-...cfargotunnel.com
                                            proxied

Cloudflare Tunnel:
  Tunnel ID: eec35fa5-4b29-4ac2-bca9-13521ecd40fd
  Connector: running on GCP VM
  Ingress: api.randsys.indevs.in -> localhost:8080

Delegation verification:
  dig +trace NS randsys.indevs.in
  # Should end with ashton.ns.cloudflare.com + rosemary.ns.cloudflare.com

================================================================
14. DEVELOPMENT WORKFLOW
================================================================

Backend change:
  1. SSH into VM
  2. Edit Java files in ~/rand-water-backend/src/
  3. Build: mvn clean package -DskipTests
  4. Restart: sudo systemctl restart randwater
  5. Test: curl -s http://localhost:8080/health
  6. git add + commit + push origin main
  7. Render auto-deploys (if not suspended)

Frontend change:
  1. On Kali: cd /home/dee/rand-water-frontend
  2. Edit files in src/pages/ or src/components/
  3. Build locally: CI=true npm run build
  4. git add + commit + push origin main
  5. Cloudflare Worker auto-deploys (~2 min)

Commit message convention:
  Fix:      Bug fixes
  Feature:  New functionality
  Docs:     Documentation
  Refactor: Code restructure
  Security: Security-related

================================================================
15. MIGRATION PLAN (November 2026)
================================================================

Trigger: GCP free trial credit expires 2026-11-20.
Cost after expiry: ~$40/month for current VM.

Target: Oracle Cloud Always Free tier
  - 2 OCPU ARM Ampere A1
  - 12 GB RAM
  - 200 GB storage
  - 10 TB/month egress
  - $0/month forever

Migration steps:
  1. Secure Oracle ARM instance (may need retry script
     for capacity)
  2. Install same stack: Debian 13, Postgres 17, Java 21,
     Maven, cloudflared
  3. Restore latest DB backup
  4. Deploy backend (git clone + build)
  5. Recreate systemd services
  6. Update cloudflared tunnel to new VM
  7. Test end-to-end
  8. Delete GCP VM

Prerequisites to have ready:
  - Latest .sql.gz backup from /home/dee_dyondzani/backups/
  - /etc/randwater.env contents
  - Cloudflare tunnel credentials JSON
  - Backend JAR (optional, can rebuild)

Fallback: GCP Always Free e2-micro in us-central1
  - Only 1 GB RAM (tight but workable with swap)
  - 1 GB/month egress limit (risk for API traffic)
  - $0/month

================================================================
16. TROUBLESHOOTING QUICK REFERENCE
================================================================

Frontend blank / 502:
  - Check Cloudflare Worker status in dashboard
  - Verify REACT_APP_API_URL in Worker env vars
  - Check Cloudflare DNS records are present

API not responding:
  - ssh into VM
  - sudo systemctl status randwater cloudflared
  - curl -s http://localhost:8080/health
  - sudo journalctl -u cloudflared -n 20

Login rejected:
  - Check backend health first
  - curl -X POST https://api.randsys.indevs.in/api/auth/login ...
  - If 401: password wrong or user inactive
  - If 500: check backend logs

Audit log shows "unknown":
  - Backend was restarted mid-request
  - Check /etc/randwater.env has JWT_SECRET set
  - Restart randwater service

Timestamps off by hours:
  - VM timezone wrong
  - timedatectl set-timezone Africa/Johannesburg
  - Restart postgresql + randwater

DB disk full:
  - Check backup_snapshot row count (keep 500)
  - Check audit_log size
  - Run VACUUM FULL on large tables

Tunnel disconnected:
  - cloudflared tunnel list (should show 2+ connections)
  - sudo systemctl restart cloudflared
  - Check Cloudflare dashboard tunnel status

================================================================
17. CONTACT & REFERENCES
================================================================

Project owner: deedyondzani (dee.dyondzani@gmail.com)
Domain: randsys.indevs.in
Primary language: English (South African)

Key external services:
  GCP Console:      console.cloud.google.com
  Cloudflare:       dash.cloudflare.com
  Stackryze:        dns.stackryze.com
  GitHub:           github.com/deedyondzani
  Render (legacy):  dashboard.render.com

Project ID (GCP):   waterlog-506514
VM name:            rand-water-vm
VM zone:            africa-south1-a
Static IP:          34.35.142.209

================================================================
END OF DOCUMENT
================================================================
