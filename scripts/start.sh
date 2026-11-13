#!/bin/bash
set -euo pipefail
cd /var/local/sport-crawl
mkdir -p /var/local/sport-crawl-config
if [ ! -f /var/local/sport-crawl-config/domain-rewrites ]; then
  printf '%s\n' '# 一行一对：左边的域名换成右边的域名。大小写都认。改完重启 api。' 'JX8.com=JX8.CC' 'qiumishuo.com=qiumishuo.cc' > /var/local/sport-crawl-config/domain-rewrites
fi
docker compose -p sport-crawl -f /var/local/sport-crawl/docker-compose.yml --project-directory /var/local/sport-crawl up -d --build --remove-orphans
docker compose -p sport-crawl-db -f /var/local/sport-crawl/docker-compose.db.yml --project-directory /var/local/sport-crawl up -d --no-build --force-recreate
cp -a /var/local/sport-crawl/site/web/. /www/wwwroot/sport-crawl-web/
chown -R www:www /www/wwwroot/sport-crawl-web

