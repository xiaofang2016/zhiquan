#!/bin/bash
set -euo pipefail
ready=0
for _ in $(seq 1 30); do
  if curl -fsS http://172.31.5.227:8892/api/sport/health >/dev/null; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" != 1 ]; then
  docker compose -p sport-crawl logs --tail 80 >&2 || true
  exit 1
fi
for _ in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8893/crawl/status >/dev/null; then
    exit 0
  fi
  sleep 2
done
docker compose -p sport-crawl-db logs --tail 80 >&2 || true
exit 1
