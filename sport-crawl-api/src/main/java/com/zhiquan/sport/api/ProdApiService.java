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
        List<DateCountItem> result = new ArrayList<>();
        for (SportMatchDate row : dates.listAll()) {
            result.add(new DateCountItem(row.getTotalMatch() == null ? 0 : row.getTotalMatch(), row.getMatchDate().toString()));
        }
        return result;
    }

    public MatchListData matchList(LocalDate date, Integer type, Integer cid, Integer ishot, int page, int size, int liveType) {
        Integer sportType = type == null || type <= 0 ? null : type;
        Integer leagueId = cid == null || cid == 0 ? null : cid;
        Integer hot = ishot == null || ishot < 0 ? null : ishot;
        // 原站热门置顶段会带上 isHot=0 的场次，例如正在进行的置顶。
        Integer topHot = ishot != null && ishot == 1 ? null : hot;
        long total = matches.count(date, ListSection.DATA, sportType, leagueId, hot);
        int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
        List<JsonNode> notop = page == 1
                ? nodes(matches.listLimited(date, ListSection.NOTOP, sportType, leagueId, hot, PINNED_LIMIT))
                : List.of();
        List<JsonNode> top = page == 1
                ? nodes(matches.listLimited(date, ListSection.TOP, sportType, leagueId, topHot, PINNED_LIMIT))
                : List.of();
        List<JsonNode> data = nodes(matches.pageNumber(date, ListSection.DATA, sportType, leagueId, hot, page, size));
        String token = DigestUtils.md5DigestAsHex((date + "|" + page + "|" + size + "|" + type + "|" + cid + "|" + ishot)
                .getBytes(StandardCharsets.UTF_8));
        return new MatchListData((int) Math.min(total, Integer.MAX_VALUE), totalPage, notop, data, top,
                liveType, date.toString(), page, token);
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
                                List<JsonNode> topList, int live_type, String starttime, int currentPage, String token) {
    }
}
