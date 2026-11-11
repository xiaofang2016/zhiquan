package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.entity.SportLeague;
import com.zhiquan.sport.store.entity.SportMatch;
import com.zhiquan.sport.store.entity.SportMatchDate;
import com.zhiquan.sport.store.repo.SportLeagueRepository;
import com.zhiquan.sport.store.repo.SportMatchDateRepository;
import com.zhiquan.sport.store.repo.SportMatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProdApiService {

    private static final int PINNED_LIMIT = 2000;

    private final SportLeagueRepository leagues;
    private final SportMatchDateRepository dates;
    private final SportMatchRepository matches;
    private final ObjectMapper objectMapper;

    public ProdApiService(SportLeagueRepository leagues,
                          SportMatchDateRepository dates,
                          SportMatchRepository matches,
                          ObjectMapper objectMapper) {
        this.leagues = leagues;
        this.dates = dates;
        this.matches = matches;
        this.objectMapper = objectMapper;
    }

    public CategoryData categories() {
        List<JsonNode> list = new ArrayList<>();
        for (SportLeague row : leagues.listAll()) {
            JsonNode node = read(row.getPayload());
            if (node != null) {
                list.add(node);
            }
        }
        return new CategoryData(list);
    }

    public List<DateCountItem> dates() {
        return dates(null, List.of(), null, false);
    }

    public List<DateCountItem> dates(Integer type, List<Integer> leagues, Long teamId, boolean anchored) {
        Integer sportType = type == null || type <= 0 ? null : type;
        List<Integer> ids = leagues == null ? List.of() : leagues;
        boolean filtered = sportType != null || !ids.isEmpty() || teamId != null || anchored;
        List<DateCountItem> result = new ArrayList<>();
        for (SportMatchDate row : dates.listAll()) {
            int total;
            if (filtered) {
                total = (int) Math.min(Integer.MAX_VALUE,
                    matches.count(row.getMatchDate(), ListSection.DATA, sportType, ids, null, teamId, anchored));
            } else {
                total = row.getTotalMatch() == null ? 0 : row.getTotalMatch();
            }
            result.add(new DateCountItem(total, row.getMatchDate().toString()));
        }
        return result;
    }

    public Object matchList(LocalDate date, Integer type, List<Integer> leagues, Long teamId, boolean anchored,
                            Integer ishot, int page, int size, int liveType, String since) {
        Integer sportType = type == null || type <= 0 ? null : type;
        List<Integer> ids = leagues == null ? List.of() : leagues;
        Integer hot = ishot == null || ishot < 0 ? null : ishot;
        // 原站热门置顶段会带上 isHot=0 的场次，例如正在进行的置顶。
        Integer topHot = ishot != null && ishot == 1 ? null : hot;
        long total = matches.count(date, ListSection.DATA, sportType, ids, hot, teamId, anchored);
        int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
        List<SportMatch> notopRows = page == 1
                ? matches.listLimited(date, ListSection.NOTOP, sportType, ids, hot, teamId, anchored, PINNED_LIMIT)
                : List.of();
        List<SportMatch> topRows = page == 1
                ? matches.listLimited(date, ListSection.TOP, sportType, ids, topHot, teamId, anchored, PINNED_LIMIT)
                : List.of();
        List<SportMatch> dataRows = matches.pageNumber(date, ListSection.DATA, sportType, ids, hot, teamId, anchored, page, size);
        String rev = revision(total, totalPage, notopRows, topRows, dataRows);
        if (since != null && !since.isBlank() && since.equals(rev)) {
            return new MatchListDelta(rev, List.of(), List.of());
        }
        String token = DigestUtils.md5DigestAsHex((date + "|" + page + "|" + size + "|" + type + "|" + ids + "|" + ishot
                + "|" + teamId + "|" + (anchored ? 1 : 0)).getBytes(StandardCharsets.UTF_8));
        return new MatchListData((int) Math.min(total, Integer.MAX_VALUE), totalPage, nodes(notopRows), nodes(dataRows), nodes(topRows),
                liveType, date.toString(), page, token, rev);
    }

    static String revision(long total, int totalPage, List<SportMatch> notop, List<SportMatch> top, List<SportMatch> data) {
        StringBuilder raw = new StringBuilder();
        raw.append(total).append('|').append(totalPage);
        append(raw, top);
        append(raw, notop);
        append(raw, data);
        return DigestUtils.md5DigestAsHex(raw.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void append(StringBuilder raw, List<SportMatch> rows) {
        for (SportMatch row : rows) {
            raw.append('\n').append(row.getSourceId()).append('#').append(row.getSortOrder()).append('#').append(row.getPayload());
        }
    }

    private List<JsonNode> nodes(List<SportMatch> rows) {
        List<JsonNode> result = new ArrayList<>();
        for (SportMatch row : rows) {
            JsonNode node = read(row.getPayload());
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    public record CategoryData(List<JsonNode> twoCategoryList) {
    }

    public record DateCountItem(int totalmatch, String matchdate) {
    }

    public record MatchListData(int total, int totalPage, List<JsonNode> notopList, List<JsonNode> dataList,
                                List<JsonNode> topList, int live_type, String starttime, int currentPage, String token,
                                String rev) {
    }

    public record MatchListDelta(String rev, List<JsonNode> changed, List<Long> removed) {
    }
}
