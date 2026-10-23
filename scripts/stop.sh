#!/bin/bash
set -euo pipefail
if [ -f /var/local/sport-crawl/docker-compose.yml ]; then
  docker compose -p sport-crawl -f /var/local/sport-crawl/docker-compose.yml down
fi
