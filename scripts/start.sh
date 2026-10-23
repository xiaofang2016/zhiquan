#!/bin/bash
set -euo pipefail
cd /var/local/sport-crawl
docker compose -p sport-crawl -f /var/local/sport-crawl/docker-compose.yml --project-directory /var/local/sport-crawl up -d --build --remove-orphans
