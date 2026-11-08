package com.zhiquan.sport.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.SourceHash;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.db.DatabaseDoc;
import com.zhiquan.sport.store.db.DatabaseKeys;
import com.zhiquan.sport.store.db.DatabaseQuery;
import com.zhiquan.sport.store.db.DatabaseTier;
import com.zhiquan.sport.store.entity.SportMatch;
import com.zhiquan.sport.store.repo.DatabaseDocRepository;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import com.zhiquan.sport.store.repo.SportMatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class DatabaseCrawlService {

    private static final TypeReference<Map<String, String>> QUERY = new TypeReference<>() {
    };

    private final DbProperties properties;
    private final SportAwsProperties aws;
    private final UpstreamClient upstream;
    private final S3DatabaseStore objects;
    private final DatabaseDocRepository docs;
    private final SportMatchRepository matches;
    private final ObjectMapper json;
    private final DbStatus status;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean open;

    public DatabaseCrawlService(DbProperties properties,
                                SportAwsProperties aws,
                                UpstreamClient upstream,
                                S3DatabaseStore objects,
                                DatabaseDocRepository docs,
                                SportMatchRepository matches,
                                ObjectMapper json,
                                DbStatus status) {
        this.properties = properties;
        this.aws = aws;
        this.upstream = upstream;
        this.objects = objects;
        this.docs = docs;
        this.matches = matches;
        this.json = json;
        this.status = status;
    }

    public void open() {
        this.open = true;
    }

    public void daily() {
        if (!lock.tryLock()) {
            log.info("资料库抓取正在进行，跳过每日任务");
            return;
        }
        try {
            status.begin("daily");
            byte[] home = pull(new DatabasePlan.DocRequest("/database/home", Map.of(), DatabaseTier.DAILY), false);
            if (home == SKIPPED) {
                throw new IllegalStateException("资料库目录没有拿到");
            }
            JsonNode homeData = json.readTree(home).path("data");
            try {
                pull(new DatabasePlan.DocRequest("/database/competition/notStarted", Map.of("pid", "4"), DatabaseTier.DAILY), false);
            } catch (Exception ex) {
                status.failed();
                log.warn("近期赛事没有抓到: {}", ex.getMessage());
            }
            fifa();
            pull(new DatabasePlan.DocRequest("/team/getFbHotCompetition", Map.of(), DatabaseTier.DAILY), false);
            List<DatabaseCatalog.Competition> competitions = new ArrayList<>(DatabaseCatalog.competitions(homeData));
            competitions.sort(Comparator.comparingInt(DatabaseCatalog.Competition::sportsType)
                    .thenComparingInt(DatabaseCatalog.Competition::id));
            Map<DatabaseCatalog.Competition, List<String>> past = new HashMap<>();
            int index = 0;
            for (DatabaseCatalog.Competition competition : competitions) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                index++;
                try {
                    past.put(competition, currentSeason(competition));
                } catch (Exception ex) {
                    if (interrupted(ex)) {
                        break;
                    }
                    status.failed();
                    log.warn("资料库联赛 {} / {} 失败: {}", competition.sportsType(), competition.id(), ex.getMessage());
                }
                if (index % 100 == 0) {
                    log.info("资料库每日进度 {}/{}", index, competitions.size());
                }
            }
            history(competitions, past);
            status.success("daily");
            log.info("资料库每日抓取结束 {}", status.snapshot());
        } catch (Exception ex) {
            status.fail("daily", ex);
            log.error("资料库每日抓取失败", ex);
        } finally {
            lock.unlock();
        }
    }

    public void live() {
        if (!open || !lock.tryLock()) {
            log.info("资料库抓取正在进行，跳过赛中刷新");
            return;
        }
        try {
            status.begin("live");
            ZoneId zone = ZoneId.of(properties.getZoneId());
            LocalDate today = LocalDate.now(zone);
            LocalDateTime now = LocalDateTime.now(zone);
            List<SportMatch> rows = new ArrayList<>();
            for (String section : List.of(ListSection.NOTOP, ListSection.TOP, ListSection.DATA)) {
                String token = null;
                do {
                    SportMatchRepository.MatchSlice slice = matches.page(today, section, null, null, null, 200, token);
                    rows.addAll(slice.items());
                    token = slice.nextToken();
                } while (token != null);
            }
            List<DatabaseCatalog.Competition> leagues = LiveLeagues.select(rows, json, now);
            log.info("资料库赛中联赛 {}", leagues.size());
            for (DatabaseCatalog.Competition competition : leagues) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                try {
                    refreshLive(competition);
                } catch (Exception ex) {
                    if (interrupted(ex)) {
                        break;
                    }
                    status.failed();
                    log.warn("资料库赛中 {} / {} 失败: {}", competition.sportsType(), competition.id(), ex.getMessage());
                }
            }
            status.success("live");
        } catch (Exception ex) {
            status.fail("live", ex);
            log.error("资料库赛中刷新失败", ex);
        } finally {
            lock.unlock();
        }
    }

    public void demand() {
        if (!open || !lock.tryLock()) {
            return;
        }
        try {
            List<DatabaseDoc> due = docs.dueDemands(Instant.now(), 20);
            for (DatabaseDoc doc : due) {
                if (doc.path() == null || doc.path().isBlank()) {
                    docs.postpone(doc.key(), Instant.now().plusSeconds(3600));
                    continue;
                }
                try {
                    Map<String, String> query = doc.query() == null || doc.query().isBlank()
                            ? Map.of()
                            : json.readValue(doc.query(), QUERY);
                    byte[] body = upstream.get(doc.path(), query);
                    store(doc.path(), query, DatabaseTier.CACHED, body, false);
                } catch (Exception ex) {
                    if (interrupted(ex)) {
                        break;
                    }
                    if (UpstreamFailure.giveUp(ex.getMessage())) {
                        log.warn("资料库按需 {} 不再重试: {}", doc.path(), ex.getMessage());
                        docs.discard(doc.key());
                        continue;
                    }
                    log.warn("资料库按需 {} 失败: {}", doc.path(), ex.getMessage());
                    docs.postpone(doc.key(), Instant.now().plusSeconds(300));
                }
            }
        } catch (Exception ex) {
            log.warn("资料库按需队列失败: {}", ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public boolean homeReady() {
        return objects.exists(DatabaseKeys.objectKey(aws.getDbPrefix(), "/database/home", Map.of()));
    }

    private void fifa() throws Exception {
        pull(new DatabasePlan.DocRequest("/team/getFifaRankClub", Map.of("rankType", "0"), DatabaseTier.DAILY), false);
        pull(new DatabasePlan.DocRequest("/team/getFifaRankClub", Map.of("rankType", "1"), DatabaseTier.DAILY), false);
        byte[] men = pull(new DatabasePlan.DocRequest("/team/getFifaRankMemTime", Map.of(), DatabaseTier.DAILY), false);
        byte[] women = pull(new DatabasePlan.DocRequest("/team/getFifaRankWomenTime", Map.of(), DatabaseTier.DAILY), false);
        if (present(men)) {
            long menTime = DatabaseCatalog.latestPubTime(json.readTree(men).path("data"));
            if (menTime > 0) {
                pull(rank("Mem", menTime), false);
            }
            for (Long time : DatabaseCatalog.olderPubTimes(json.readTree(men).path("data"))) {
                pull(rank("Mem", time), true);
            }
        }
        if (present(women)) {
            long womenTime = DatabaseCatalog.latestPubTime(json.readTree(women).path("data"));
            if (womenTime > 0) {
                pull(rank("Women", womenTime), false);
            }
            for (Long time : DatabaseCatalog.olderPubTimes(json.readTree(women).path("data"))) {
                pull(rank("Women", time), true);
            }
        }
    }

    private static DatabasePlan.DocRequest rank(String kind, long pubTime) {
        return new DatabasePlan.DocRequest("/team/getFifaRank" + kind,
                Map.of("regionId", "0", "pubTime", Long.toString(pubTime)), DatabaseTier.FROZEN);
    }

    private List<String> currentSeason(DatabaseCatalog.Competition competition) throws Exception {
        byte[] yearsBody = pull(new DatabasePlan.DocRequest(
                DatabasePlan.yearPath(competition),
                Map.of("competitionId", Integer.toString(competition.id())),
                DatabaseTier.DAILY), false);
        JsonNode years = present(yearsBody) ? json.readTree(yearsBody).path("data") : null;
        String year = DatabaseCatalog.currentYear(years);
        byte[] stageBody = null;
        for (DatabasePlan.DocRequest request : DatabasePlan.season(competition, year)) {
            byte[] body = pull(request, false);
            if (request.path().endsWith("getCompetitionStageList")) {
                stageBody = body;
            }
        }
        List<DatabasePlan.Stage> stages = present(stageBody)
                ? DatabaseCatalog.currentStages(json.readTree(stageBody).path("data"))
                : List.of();
        for (DatabasePlan.DocRequest request : DatabasePlan.rounds(competition, year, stages)) {
            pull(request, false);
        }
        return DatabaseCatalog.otherYears(years, year);
    }

    private void refreshLive(DatabaseCatalog.Competition competition) throws Exception {
        byte[] yearsBody = pull(new DatabasePlan.DocRequest(
                DatabasePlan.yearPath(competition),
                Map.of("competitionId", Integer.toString(competition.id())),
                DatabaseTier.LIVE), false);
        String year = present(yearsBody) ? DatabaseCatalog.currentYear(json.readTree(yearsBody).path("data")) : "";
        byte[] stageBody = pull(new DatabasePlan.DocRequest(
                "/team/getCompetitionStageList",
                Map.of("type", Integer.toString(competition.sportsType()), "cid", Integer.toString(competition.id())),
                DatabaseTier.LIVE), false);
        List<DatabasePlan.Stage> stages = present(stageBody)
                ? DatabaseCatalog.currentStages(json.readTree(stageBody).path("data"))
                : List.of();
        for (DatabasePlan.DocRequest request : DatabasePlan.live(competition, year, stages)) {
            pull(request, false);
        }
    }

    private void history(List<DatabaseCatalog.Competition> competitions,
                         Map<DatabaseCatalog.Competition, List<String>> past) {
        if (competitions.isEmpty()) {
            return;
        }
        int budget = properties.getHistoryBudget();
        int idle = 0;
        int index = docs.historyCursor();
        int guard = 0;
        while (budget > 0 && idle < properties.getHistoryScanCap() && guard < competitions.size()) {
            DatabaseCatalog.Competition competition = competitions.get(Math.floorMod(index, competitions.size()));
            List<String> years = past.get(competition);
            guard++;
            if (years == null || years.isEmpty()) {
                index++;
                continue;
            }
            boolean blocked = false;
            for (String year : years) {
                for (DatabasePlan.DocRequest request : DatabasePlan.past(competition, year)) {
                    if (budget <= 0 || idle >= properties.getHistoryScanCap()) {
                        blocked = true;
                        break;
                    }
                    try {
                        byte[] body = pull(request, true);
                        if (body == SKIPPED) {
                            idle++;
                        } else {
                            budget--;
                            idle = 0;
                        }
                    } catch (Exception ex) {
                        if (interrupted(ex)) {
                            docs.historyCursor(Math.floorMod(index, competitions.size()));
                            return;
                        }
                        status.failed();
                        budget--;
                        log.warn("资料库历史 {} / {} {} 失败: {}", competition.sportsType(), competition.id(), year, ex.getMessage());
                    }
                }
                if (blocked) {
                    break;
                }
            }
            if (blocked) {
                break;
            }
            index++;
        }
        docs.historyCursor(Math.floorMod(index, competitions.size()));
    }

    private static final byte[] SKIPPED = new byte[0];

    private byte[] pull(DatabasePlan.DocRequest request, boolean freezeSkip) throws Exception {
        Map<String, String> query = DatabaseQuery.normalize(request.query());
        String key = DatabaseKeys.objectKey(aws.getDbPrefix(), request.path(), query);
        if (freezeSkip) {
            DatabaseDoc existing = docs.find(key);
            if (existing != null && existing.sha256() != null && existing.sha256().length() == 64) {
                status.skipped();
                return SKIPPED;
            }
        }
        byte[] body = upstream.get(request.path(), query);
        store(request.path(), query, request.tier(), body, true);
        return body;
    }

    private void store(String path, Map<String, String> query, String tier, byte[] body, boolean account) throws Exception {
        Map<String, String> normalized = DatabaseQuery.normalize(query);
        String key = DatabaseKeys.objectKey(aws.getDbPrefix(), path, normalized);
        String hash = SourceHash.sha256(body);
        DatabaseDoc existing = docs.find(key);
        Instant now = Instant.now();
        if (existing != null && hash.equals(existing.sha256())) {
            docs.touch(key, tier, now);
            if (account) {
                status.skipped();
            }
            return;
        }
        objects.put(key, body);
        docs.save(key, hash, tier, body.length, path, json.writeValueAsString(normalized), now);
        if (account) {
            status.fetched();
        }
    }

    private static boolean present(byte[] body) {
        return body != null && body != SKIPPED;
    }

    private static boolean interrupted(Exception ex) {
        return ex instanceof IllegalStateException && ex.getCause() instanceof InterruptedException
                || Thread.currentThread().isInterrupted();
    }
}
