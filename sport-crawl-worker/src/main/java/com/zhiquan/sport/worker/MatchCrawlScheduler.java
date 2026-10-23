package com.zhiquan.sport.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MatchCrawlScheduler {

    private final CrawlProperties properties;
    private final CrawlGate gate;
    private final CrawlStatus status;
    private final MatchCrawlService matches;

    public MatchCrawlScheduler(CrawlProperties properties,
                               CrawlGate gate,
                               CrawlStatus status,
                               MatchCrawlService matches) {
        this.properties = properties;
        this.gate = gate;
        this.status = status;
        this.matches = matches;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isEnabled() || !properties.isBackfillOnStart()) {
            status.markBackfillFinished();
            return;
        }
        Thread thread = new Thread(this::backfill, "sport-backfill");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.MINUTES, initialDelay = 2)
    public void today() {
        if (!properties.isEnabled()) {
            return;
        }
        gate.awaitRun("today", () -> {
            matches.syncLeagues();
            matches.syncDates();
            matches.crawlDate(matches.today());
        });
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES, initialDelay = 30)
    public void future() {
        if (!properties.isEnabled() || !status.isBackfillFinished()) {
            return;
        }
        List<LocalDate> dates = matches.futureDates(matches.syncDates());
        for (LocalDate date : dates) {
            gate.awaitRun("future-" + date, () -> matches.crawlDate(date));
        }
    }

    @Scheduled(cron = "0 0 4 * * ?", zone = "Asia/Shanghai")
    public void past() {
        if (!properties.isEnabled() || !status.isBackfillFinished()) {
            return;
        }
        List<LocalDate> dates = matches.pastDates(matches.syncDates());
        for (LocalDate date : dates) {
            gate.awaitRun("past-" + date, () -> matches.crawlDate(date));
        }
    }

    public void backfill() {
        try {
            gate.awaitRun("backfill-today", () -> {
                matches.syncLeagues();
                matches.syncDates();
                matches.crawlDate(matches.today());
            });
            List<LocalDate> dates = matches.syncDates();
            for (LocalDate date : matches.futureDates(dates)) {
                gate.awaitRun("backfill-" + date, () -> matches.crawlDate(date));
            }
            for (LocalDate date : matches.pastDates(dates)) {
                gate.awaitRun("backfill-" + date, () -> matches.crawlDate(date));
            }
        } finally {
            status.markBackfillFinished();
            log.info("backfill finished");
        }
    }
}
