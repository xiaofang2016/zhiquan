#!/bin/bash
set -euo pipefail
for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8892/api/sport/health >/dev/null; then
    exit 0
  fi
  sleep 2
done
docker compose -p sport-crawl logs --tail 80 >&2 || true
exit 1
