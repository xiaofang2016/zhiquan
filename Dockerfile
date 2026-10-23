FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --uid 10001 --home /opt/sport-crawl sport
WORKDIR /opt/sport-crawl
COPY worker.jar api.jar ./
USER sport
