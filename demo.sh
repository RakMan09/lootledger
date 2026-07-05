#!/usr/bin/env bash
#
# LootLedger self-verifying demo.
#
# Drives a running LootLedger instance to *prove* — live — that the economy cannot be duped.
# Start the app first (either works):
#     docker compose up -d && ./gradlew bootRun
#   or the Postgres-only demo profile:
#     docker compose up -d postgres && SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
#
# Then run:  ./demo.sh
#
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
THREADS="${THREADS:-200}"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
green() { printf "\033[32m%s\033[0m\n" "$1"; }
red() { printf "\033[31m%s\033[0m\n" "$1"; }

echo
bold "== LootLedger live proof =="
echo "API: $BASE"
echo

# 0. Wait for the API to be up.
printf "Waiting for API"
for i in $(seq 1 60); do
  if curl -fs "$BASE/actuator/health" >/dev/null 2>&1; then echo " — up."; break; fi
  printf "."; sleep 1
  if [ "$i" = "60" ]; then echo; red "API never became healthy at $BASE"; exit 1; fi
done
echo

# 1. Seed some players with gold.
bold "1) Seeding players with gold"
python3 load/players.py --base "$BASE" seed --players 20 --gold 1000000
echo

# 2. Benchmark throughput + latency.
bold "2) Benchmarking transfers (throughput + latency)"
python3 load/players.py --base "$BASE" load --players 20 --requests 3000 --concurrency 32
echo

# 3. The headline: a duplicate storm must NOT dupe gold.
bold "3) Duplicate storm — firing $THREADS identical requests with ONE Idempotency-Key"
python3 load/players.py --base "$BASE" dupe-storm --threads "$THREADS"
echo

# 4. The auditor: prove all invariants still hold.
bold "4) Reconciliation — proving conservation of value"
RECON="$(curl -fs "$BASE/admin/reconciliation")"
echo "$RECON"
if echo "$RECON" | grep -q '"ok":true'; then
  green "PASS: all invariants hold — value is conserved, nothing duped."
else
  red "FAIL: reconciliation detected drift!"
  exit 1
fi
echo
green "== Demo complete: the economy survived every attack. =="
