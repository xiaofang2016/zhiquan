#!/bin/bash
set -euo pipefail
cd /var/local/sport-crawl
docker compose -p sport-crawl -f /var/local/sport-crawl/docker-compose.yml --project-directory /var/local/sport-crawl up -d --build --remove-orphans
docker compose -p sport-crawl-db -f /var/local/sport-crawl/docker-compose.db.yml --project-directory /var/local/sport-crawl up -d --no-build --force-recreate
