#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "Bitte .env aus .env.example erstellen"; exit 1; }
set -a; source .env; set +a

docker compose up -d postgres
echo "Postgres gestartet, warte auf Healthcheck..."
until docker exec noonoo-postgres pg_isready -U noonoo >/dev/null 2>&1; do sleep 1; done
echo "Postgres bereit."

./gradlew :aggregator:run --console=plain &
AGG_PID=$!
./gradlew :web:run --console=plain &
WEB_PID=$!

trap "echo 'Stoppe...'; kill $AGG_PID $WEB_PID 2>/dev/null || true" EXIT INT TERM

echo ""
echo "Aggregator (PID $AGG_PID) und Web (PID $WEB_PID) laufen."
echo "Web-Frontend: http://localhost:8080"
echo "Beenden mit Ctrl+C"
echo ""
wait
