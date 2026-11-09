package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.MatchKeys;
import com.zhiquan.sport.store.entity.SportLeague;
import com.zhiquan.sport.store.entity.SportMatch;
import com.zhiquan.sport.store.entity.SportMatchDate;
import com.zhiquan.sport.store.repo.SportLeagueRepository;
import com.zhiquan.sport.store.repo.SportMatchDateRepository;
import com.zhiquan.sport.store.repo.SportMatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MatchCrawlService {

    private final KzbClient client;
    private final CrawlProperties properties;
    private final CrawlStatus status;
    private final MatchRowParser parser;
    private final ImageDownloadService images;
    private final ObjectMapper objectMapper;
    private final SportLeagueRepository leagueMapper;
    private final SportMatchDateRepository dateMapper;
    private final SportMatchRepository matchMapper;

    public MatchCrawlService(KzbClient client,
                             CrawlProperties properties,
                             CrawlStatus status,
                             MatchRowParser parser,
                             ImageDownloadService images,
                             ObjectMapper objectMapper,
                             SportLeagueRepository leagueMapper,
                             SportMatchDateRepository dateMapper,
                             SportMatchRepository matchMapper) {
        this.client = client;
        this.properties = properties;
        this.status = status;
        this.parser = parser;
        this.images = images;
        this.objectMapper = objectMapper;
        this.leagueMapper = leagueMapper;
        this.dateMapper = dateMapper;
        this.matchMapper = matchMapper;
    }

    public ZoneId zone() {
        return ZoneId.of(properties.getZoneId());
    }

    public LocalDate today() {
        return LocalDate.now(zone());
    }

    public void syncLeagues() {
        JsonNode list = client.categoryList().path("data").path("twoCategoryList");
        if (!list.isArray()) {
            throw new IllegalStateException("category list missing");
        }
        List<JsonNode> nodes = new ArrayList<>();
        int order = 0;
        for (JsonNode item : list) {
            Integer sourceId = item.path("id").isNumber() ? item.path("id").asInt() : null;
            Integer sportType = item.path("type").isNumber() ? item.path("type").asInt() : null;
            if (sourceId == null || sportType == null) {
                continue;
            }
            SportLeague row = new SportLeague();
            row.setSourceId(sourceId);
            row.setSportType(sportType);
            row.setSortOrder(order++);
            try {
                row.setPayload(objectMapper.writeValueAsString(item));
            } catch (Exception ex) {
                throw new IllegalStateException("league payload", ex);
            }
            leagueMapper.upsert(row);
            nodes.add(item);
        }
        images.downloadNodes(nodes);
        log.info("leagues upserted {}", nodes.size());
    }

    public List<LocalDate> syncDates() {
        JsonNode list = client.dateCount().path("data");
        if (!list.isArray()) {
            throw new IllegalStateException("date count missing");
        }
        List<LocalDate> dates = new ArrayList<>();
        for (JsonNode item : list) {
            String text = item.path("matchdate").asText("");
            if (text.isBlank()) {
                continue;
            }
            LocalDate date = LocalDate.parse(text);
            SportMatchDate row = new SportMatchDate();
            row.setMatchDate(date);
            row.setTotalMatch(item.path("totalmatch").asInt(0));
            dateMapper.upsert(row);
            dates.add(date);
        }
        log.info("dates upserted {}", dates.size());
        return dates;
    }

    public void crawlDate(LocalDate date) {
        int requested = properties.getPageSize();
        JsonNode data = client.matchList(date, 1, requested).path("data");
        int total = data.path("total").asInt(0);
        int totalPage = data.path("totalPage").asInt(0);
        int dataSize = arraySize(data, "dataList");
        if (MatchListPages.pageSizeIgnored(total, totalPage, dataSize, requested)) {
            requested = properties.getFallbackPageSize();
            log.info("match date {} page size ignored, retry ps={}", date, requested);
            data = client.matchList(date, 1, requested).path("data");
            total = data.path("total").asInt(0);
            totalPage = data.path("totalPage").asInt(0);
            dataSize = arraySize(data, "dataList");
        }
        JsonNode second = null;
        if (totalPage > 1) {
            second = client.matchList(date, 2, requested).path("data");
            int reported = totalPage;
            totalPage = MatchListPages.laterPageCount(totalPage, second.path("totalPage").asInt(0));
            total = Math.max(total, second.path("total").asInt(0));
            if (totalPage != reported) {
                log.info("match date {} page count {} widened to {}", date, reported, totalPage);
            }
        }
        int pages = MatchListPages.pageCount(total, totalPage, dataSize, requested, 0);
        int capped = MatchListPages.pageCount(total, totalPage, dataSize, requested, properties.getMaxPages());
        if (capped < 1) {
            capped = hasRows(data) ? 1 : 0;
            pages = capped;
        }
        log.info("match date {} total={} pages={} pageSize={}", date, total, capped, requested);
        Set<Long> pinned = new HashSet<>();
        Set<String> kept = new HashSet<>();
        if (capped > 0) {
            status.page(date, 1, capped);
            ingest(date, data, 1, true, pinned, kept, false);
            if (capped >= 2) {
                JsonNode pageData = second == null ? client.matchList(date, 2, requested).path("data") : second;
                status.page(date, 2, capped);
                ingest(date, pageData, 2, false, pinned, kept, false);
            }
            for (int page = 3; page <= capped; page++) {
                JsonNode pageData = client.matchList(date, page, requested).path("data");
                status.page(date, page, capped);
                ingest(date, pageData, page, false, pinned, kept, false);
            }
        }
        if (capped < pages) {
            log.warn("match date {} stopped at {} of {} pages, leave existing rows", date, capped, pages);
            return;
        }
        removeStale(date, kept);
    }

    /**
     * 明天起主播名单还没排出来，带主播的列表是空的。这里改抓全部场次，只留下开赛日是这一天的。
     */
    public void crawlUpcoming(LocalDate date) {
        crawlFullDay(date, 30);
    }

    /** 昨天和过去几天的赛果。全部场次，只留下开赛日是这一天的。 */
    public void crawlResults(LocalDate date) {
        crawlFullDay(date, properties.getMaxPages());
    }

    private void crawlFullDay(LocalDate date, int cap) {
        int requested = properties.getPageSize();
        JsonNode data = upcomingPage(date, 1, requested);
        int dataSize = arraySize(data, "dataList");
        if (MatchListPages.pageSizeIgnored(data.path("total").asInt(0), data.path("totalPage").asInt(0), dataSize, requested)) {
            requested = properties.getFallbackPageSize();
            log.info("match date {} page size ignored, retry ps={}", date, requested);
            data = upcomingPage(date, 1, requested);
        }
        Set<Long> pinned = new HashSet<>();
        Set<String> kept = new HashSet<>();
        boolean seen = false;
        boolean done = false;
        int page = 1;
        while (page <= cap) {
            if (page > 1) {
                data = upcomingPage(date, page, requested);
            }
            if (!hasRows(data)) {
                done = true;
                break;
            }
            if (MatchDayPages.past(date, data) && (seen || page == 1)) {
                done = true;
                break;
            }
            ingest(date, data, page, page == 1, pinned, kept, true);
            if (MatchDayPages.has(date, data)) {
                seen = true;
            }
            page++;
        }
        if (!done) {
            log.warn("match date {} full list stopped at {} pages, leave existing rows", date, cap);
            return;
        }
        removeStale(date, kept);
    }

    private JsonNode upcomingPage(LocalDate date, int page, int pageSize) {
        return client.matchList(date, page, pageSize, 0).path("data");
    }

    private void removeStale(LocalDate date, Set<String> kept) {
        int removed = matchMapper.deleteAbsent(date, kept);
        if (removed > 0) {
            log.info("match date {} removed stale {}", date, removed);
        }
    }

    public List<LocalDate> futureDates(List<LocalDate> dates) {
        LocalDate today = today();
        return dates.stream().filter(date -> date.isAfter(today)).sorted().toList();
    }

    public List<LocalDate> pastDates(List<LocalDate> dates) {
        return pastWithin(dates, today(), 7);
    }

    static List<LocalDate> pastWithin(List<LocalDate> dates, LocalDate today, int days) {
        LocalDate earliest = today.minusDays(days);
        return dates.stream()
                .filter(date -> !date.isBefore(earliest) && date.isBefore(today))
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
    }

    private void ingest(LocalDate date, JsonNode data, int pageNumber, boolean firstPage,
                        Set<Long> pinned, Set<String> kept, boolean dayOnly) {
        if (firstPage) {
            saveSection(date, data, "notopList", ListSection.NOTOP, pageNumber, pinned, kept, dayOnly);
            saveSection(date, data, "topList", ListSection.TOP, pageNumber, pinned, kept, dayOnly);
        }
        saveSection(date, data, "dataList", ListSection.DATA, pageNumber, pinned, kept, dayOnly);
    }

    private void saveSection(LocalDate date, JsonNode data, String field, String section,
                             int pageNumber, Set<Long> pinned, Set<String> kept, boolean dayOnly) {
        JsonNode list = data.get(field);
        if (list == null || !list.isArray() || list.isEmpty()) {
            return;
        }
        List<JsonNode> saved = new ArrayList<>();
        int index = 0;
        for (JsonNode item : list) {
            SportMatch row = parser.toRow(item, date, section, pageNumber, index);
            index++;
            if (row == null) {
                continue;
            }
            if (dayOnly && (row.getMatchTime() == null || !date.equals(row.getMatchTime().toLocalDate()))) {
                continue;
            }
            kept.add(MatchKeys.itemKey(date, row.getSourceId()));
            if (ListSection.DATA.equals(section) && pinned.contains(row.getSourceId())) {
                continue;
            }
            if (!ListSection.DATA.equals(section)) {
                pinned.add(row.getSourceId());
            }
            matchMapper.upsert(row);
            status.addMatch();
            saved.add(item);
        }
        images.downloadNodes(saved);
        log.info("match date {} {} page {} saved {}", date, section, pageNumber, saved.size());
    }

    private static int arraySize(JsonNode data, String field) {
        JsonNode list = data.get(field);
        return list != null && list.isArray() ? list.size() : 0;
    }

    private static boolean hasRows(JsonNode data) {
        return arraySize(data, "notopList") > 0 || arraySize(data, "topList") > 0 || arraySize(data, "dataList") > 0;
    }
}
