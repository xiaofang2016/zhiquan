package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.MatchKeys;
import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.aws.KeyToken;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportMatch;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class SportMatchRepository {

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public SportMatchRepository(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public void upsert(SportMatch row) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("source_id", Attrs.s(MatchKeys.itemKey(row.getListDate(), row.getSourceId())));
        item.put("list_date", Attrs.s(row.getListDate().toString()));
        item.put("list_key", Attrs.s(MatchKeys.listKey(row.getListSection(), row.getSortOrder(), row.getSourceId())));
        item.put("date_cid", Attrs.s(MatchKeys.dateCid(row.getListDate(), row.getCid())));
        if (row.getCid() != null) {
            item.put("cid", Attrs.n(row.getCid()));
        }
        if (row.getSportType() != null) {
            item.put("sport_type", Attrs.n(row.getSportType()));
        }
        item.put("is_hot", Attrs.n(row.getIsHot() == null ? 0 : row.getIsHot()));
        if (row.getHomeTeamId() != null) {
            item.put("hteam_id", Attrs.n(row.getHomeTeamId()));
        }
        if (row.getAwayTeamId() != null) {
            item.put("ateam_id", Attrs.n(row.getAwayTeamId()));
        }
        if (row.getHasAnchor() != null) {
            item.put("has_anchor", Attrs.n(row.getHasAnchor()));
        }
        item.put("list_section", Attrs.s(row.getListSection()));
        item.put("sort_order", Attrs.n(row.getSortOrder() == null ? 0 : row.getSortOrder()));
        item.put("payload", Attrs.s(row.getPayload()));
        if (row.getMatchTime() != null) {
            item.put("match_time", Attrs.s(row.getMatchTime().toString()));
        }
        item.put("updated_at", Attrs.s(Attrs.now()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getMatchTable()).item(item).build());
    }

    public SportMatch find(LocalDate date, long sourceId) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getMatchTable())
                .key(Map.of("source_id", Attrs.s(MatchKeys.itemKey(date, sourceId))))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return null;
        }
        return toMatch(item);
    }

    public int deleteAbsent(LocalDate date, Set<String> kept) {
        int removed = 0;
        for (String key : itemKeys(date)) {
            if (kept.contains(key)) {
                continue;
            }
            dynamo.deleteItem(DeleteItemRequest.builder()
                    .tableName(aws.getMatchTable())
                    .key(Map.of("source_id", Attrs.s(key)))
                    .build());
            removed++;
        }
        return removed;
    }

    private Set<String> itemKeys(LocalDate date) {
        Set<String> keys = new HashSet<>();
        for (String section : List.of(ListSection.NOTOP, ListSection.TOP, ListSection.DATA)) {
            Map<String, AttributeValue> start = null;
            int guard = 0;
            do {
                QueryResponse response = dynamo.query(request(date, section, null, null, null, null, false, start)
                        .projectionExpression("source_id")
                        .build());
                for (Map<String, AttributeValue> item : response.items()) {
                    String key = Attrs.str(item, "source_id");
                    if (key != null) {
                        keys.add(key);
                    }
                }
                start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
            } while (start != null && ++guard < 5000);
        }
        return keys;
    }

    public long count(LocalDate date, String section, Integer sportType, Integer cid, Integer hot) {
        return count(date, section, sportType, one(cid), hot, null, false);
    }

    public long count(LocalDate date, String section, Integer sportType, List<Integer> cids, Integer hot,
                      Long teamId, boolean anchored) {
        List<Integer> leagues = cids == null ? List.of() : cids;
        if (leagues.size() > 1) {
            long total = 0;
            for (Integer cid : leagues) {
                total += countOne(date, section, sportType, cid, hot, teamId, anchored);
            }
            return total;
        }
        return countOne(date, section, sportType, leagues.isEmpty() ? null : leagues.get(0), hot, teamId, anchored);
    }

    public List<SportMatch> listLimited(LocalDate date, String section, Integer sportType, Integer cid, Integer hot, int limit) {
        return listLimited(date, section, sportType, one(cid), hot, null, false, limit);
    }

    public List<SportMatch> listLimited(LocalDate date, String section, Integer sportType, List<Integer> cids, Integer hot,
                                        Long teamId, boolean anchored, int limit) {
        List<Integer> leagues = cids == null ? List.of() : cids;
        if (leagues.size() > 1) {
            List<SportMatch> merged = merged(date, section, sportType, leagues, hot, teamId, anchored);
            return merged.subList(0, Math.min(limit, merged.size()));
        }
        return page(date, section, sportType, leagues.isEmpty() ? null : leagues.get(0), hot, teamId, anchored, limit, null).items();
    }

    public List<SportMatch> pageNumber(LocalDate date, String section, Integer sportType, Integer cid, Integer hot,
                                       int page, int size) {
        return pageNumber(date, section, sportType, one(cid), hot, null, false, page, size);
    }

    public List<SportMatch> pageNumber(LocalDate date, String section, Integer sportType, List<Integer> cids, Integer hot,
                                       Long teamId, boolean anchored, int page, int size) {
        List<Integer> leagues = cids == null ? List.of() : cids;
        if (leagues.size() > 1) {
            List<SportMatch> merged = merged(date, section, sportType, leagues, hot, teamId, anchored);
            int from = Math.max(0, page - 1) * size;
            if (from >= merged.size()) {
                return List.of();
            }
            return merged.subList(from, Math.min(from + size, merged.size()));
        }
        Integer cid = leagues.isEmpty() ? null : leagues.get(0);
        int skip = Math.max(0, page - 1) * size;
        Map<String, AttributeValue> start = null;
        int skipped = 0;
        List<SportMatch> collected = new ArrayList<>();
        boolean narrow = sportType != null || hot != null || teamId != null || anchored;
        int queryLimit = narrow ? Math.max(size, 200) : 300;
        int guard = 0;
        while (collected.size() < size && guard++ < 5000) {
            QueryResponse response = dynamo.query(request(date, section, sportType, cid, hot, teamId, anchored, start)
                    .limit(queryLimit)
                    .build());
            for (Map<String, AttributeValue> item : response.items()) {
                if (skipped < skip) {
                    skipped++;
                    continue;
                }
                collected.add(toMatch(item));
                if (collected.size() == size) {
                    return collected;
                }
            }
            if (!response.hasLastEvaluatedKey()) {
                return collected;
            }
            start = response.lastEvaluatedKey();
        }
        return collected;
    }

    public MatchSlice page(LocalDate date, String section, Integer sportType, Integer cid, Integer hot, int size, String nextToken) {
        return page(date, section, sportType, cid, hot, null, false, size, nextToken);
    }

    private MatchSlice page(LocalDate date, String section, Integer sportType, Integer cid, Integer hot,
                            Long teamId, boolean anchored, int size, String nextToken) {
        Map<String, AttributeValue> start = KeyToken.decode(nextToken);
        List<SportMatch> collected = new ArrayList<>();
        boolean byCid = cid != null;
        boolean narrow = sportType != null || hot != null || teamId != null || anchored;
        int pageLimit = narrow ? Math.max(size, 200) : size;
        int guard = 0;
        while (collected.size() < size && guard++ < 5000) {
            QueryResponse response = dynamo.query(request(date, section, sportType, cid, hot, teamId, anchored, start)
                    .limit(pageLimit)
                    .build());
            List<Map<String, AttributeValue>> items = response.items();
            for (int i = 0; i < items.size(); i++) {
                collected.add(toMatch(items.get(i)));
                if (collected.size() == size) {
                    boolean end = i == items.size() - 1 && !response.hasLastEvaluatedKey();
                    return new MatchSlice(collected, end ? null : KeyToken.encode(indexKey(items.get(i), byCid)));
                }
            }
            if (!response.hasLastEvaluatedKey()) {
                return new MatchSlice(collected, null);
            }
            start = response.lastEvaluatedKey();
        }
        return new MatchSlice(collected, start == null ? null : KeyToken.encode(start));
    }

    private long countOne(LocalDate date, String section, Integer sportType, Integer cid, Integer hot,
                          Long teamId, boolean anchored) {
        Map<String, AttributeValue> start = null;
        long total = 0;
        int guard = 0;
        do {
            QueryResponse response = dynamo.query(request(date, section, sportType, cid, hot, teamId, anchored, start)
                    .select(Select.COUNT)
                    .build());
            total += response.count();
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null && ++guard < 5000);
        return total;
    }

    private List<SportMatch> merged(LocalDate date, String section, Integer sportType, List<Integer> cids, Integer hot,
                                   Long teamId, boolean anchored) {
        List<SportMatch> rows = new ArrayList<>();
        for (Integer cid : cids) {
            rows.addAll(listAll(date, section, sportType, cid, hot, teamId, anchored));
        }
        rows.sort(Comparator
                .comparingInt((SportMatch row) -> row.getSortOrder() == null ? 0 : row.getSortOrder())
                .thenComparingLong(row -> row.getSourceId() == null ? 0L : row.getSourceId()));
        return rows;
    }

    private List<SportMatch> listAll(LocalDate date, String section, Integer sportType, Integer cid, Integer hot,
                                    Long teamId, boolean anchored) {
        List<SportMatch> rows = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        int guard = 0;
        do {
            QueryResponse response = dynamo.query(request(date, section, sportType, cid, hot, teamId, anchored, start)
                    .limit(300)
                    .build());
            for (Map<String, AttributeValue> item : response.items()) {
                rows.add(toMatch(item));
            }
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null && ++guard < 5000);
        return rows;
    }

    private static List<Integer> one(Integer cid) {
        return cid == null ? List.of() : List.of(cid);
    }

    private QueryRequest.Builder request(LocalDate date, String section, Integer sportType, Integer cid, Integer hot,
                                         Long teamId, boolean anchored, Map<String, AttributeValue> start) {
        if (!ListSection.DATA.equals(section) && !ListSection.TOP.equals(section) && !ListSection.NOTOP.equals(section)) {
            throw new IllegalArgumentException("section");
        }
        boolean byCid = cid != null;
        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        names.put("#h", byCid ? "date_cid" : "list_date");
        names.put("#k", "list_key");
        values.put(":h", Attrs.s(byCid ? MatchKeys.dateCid(date, cid) : date.toString()));
        values.put(":p", Attrs.s(section + "#"));
        String filter = filter(names, values, sportType, hot, teamId, anchored);
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(aws.getMatchTable())
                .indexName(byCid ? "by_date_cid" : "by_date")
                .keyConditionExpression("#h = :h AND begins_with(#k, :p)")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .scanIndexForward(true);
        if (filter != null) {
            request.filterExpression(filter);
        }
        if (start != null) {
            request.exclusiveStartKey(start);
        }
        return request;
    }

    private static String filter(Map<String, String> names, Map<String, AttributeValue> values, Integer sportType, Integer hot,
                                 Long teamId, boolean anchored) {
        List<String> parts = new ArrayList<>();
        if (sportType != null) {
            names.put("#type", "sport_type");
            values.put(":type", Attrs.n(sportType));
            parts.add("#type = :type");
        }
        if (hot != null) {
            names.put("#hot", "is_hot");
            values.put(":hot", Attrs.n(hot));
            parts.add("#hot = :hot");
        }
        if (teamId != null) {
            names.put("#home", "hteam_id");
            names.put("#away", "ateam_id");
            values.put(":team", Attrs.n(teamId));
            parts.add("(#home = :team OR #away = :team)");
        }
        if (anchored) {
            names.put("#anchor", "has_anchor");
            values.put(":one", Attrs.n(1));
            parts.add("(attribute_not_exists(#anchor) OR #anchor = :one)");
        }
        return parts.isEmpty() ? null : String.join(" AND ", parts);
    }

    private static Map<String, AttributeValue> indexKey(Map<String, AttributeValue> item, boolean byCid) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(byCid ? "date_cid" : "list_date", item.get(byCid ? "date_cid" : "list_date"));
        key.put("list_key", item.get("list_key"));
        key.put("source_id", item.get("source_id"));
        return key;
    }

    private static SportMatch toMatch(Map<String, AttributeValue> item) {
        SportMatch row = new SportMatch();
        String sourceKey = Attrs.str(item, "source_id");
        if (sourceKey != null) {
            row.setSourceId(MatchKeys.matchId(sourceKey));
        }
        row.setCid(Attrs.num(item, "cid"));
        row.setSportType(Attrs.num(item, "sport_type"));
        String listDate = Attrs.str(item, "list_date");
        if (listDate != null) {
            row.setListDate(LocalDate.parse(listDate));
        }
        String matchTime = Attrs.str(item, "match_time");
        if (matchTime != null) {
            row.setMatchTime(LocalDateTime.parse(matchTime));
        }
        row.setIsHot(Attrs.num(item, "is_hot"));
        row.setHomeTeamId(Attrs.lng(item, "hteam_id"));
        row.setAwayTeamId(Attrs.lng(item, "ateam_id"));
        row.setHasAnchor(Attrs.num(item, "has_anchor"));
        row.setListSection(Attrs.str(item, "list_section"));
        row.setSortOrder(Attrs.num(item, "sort_order"));
        row.setPayload(Attrs.str(item, "payload"));
        row.setUpdatedAt(Attrs.time(Attrs.str(item, "updated_at")));
        return row;
    }

    public record MatchSlice(List<SportMatch> items, String nextToken) {
    }
}
