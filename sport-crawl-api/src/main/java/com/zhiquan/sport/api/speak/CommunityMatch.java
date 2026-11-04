package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.entity.SportMatch;
import com.zhiquan.sport.store.repo.SportMatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/** 用最近三天的赛程给帖子上的比赛补比分。没有这场就留空。 */
@Service
public class CommunityMatch {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final SportMatchRepository matches;
    private final ObjectMapper json;

    public CommunityMatch(SportMatchRepository matches, ObjectMapper json) {
        this.matches = matches;
        this.json = json;
    }

    public void fill(JsonNode body) {
        if (body == null) {
            return;
        }
        Map<Long, JsonNode> cache = new HashMap<>();
        if (body.path("posts").isArray()) {
            for (JsonNode post : body.path("posts")) {
                apply(post.path("match"), cache);
            }
            return;
        }
        apply(body.path("match"), cache);
    }

    private void apply(JsonNode match, Map<Long, JsonNode> cache) {
        if (!(match instanceof ObjectNode object) || !object.path("id").canConvertToLong()) {
            return;
        }
        JsonNode payload = cache.computeIfAbsent(object.path("id").asLong(), this::payload);
        if (payload == null) {
            object.putNull("score");
            object.putNull("status");
            return;
        }
        putText(object, "home", first(payload, "hteam_name", "home"));
        putText(object, "away", first(payload, "ateam_name", "away"));
        putText(object, "homeLogo", first(payload, "hteam_logo", "homeLogo"));
        putText(object, "awayLogo", first(payload, "ateam_logo", "awayLogo"));
        String score = payload.path("score").asText("");
        if (score.isEmpty()) {
            object.putNull("score");
        } else {
            object.put("score", score);
        }
        String status = payload.path("status_up_name").asText("");
        if (status.isEmpty() || status.chars().allMatch(Character::isDigit)) {
            object.putNull("status");
        } else {
            object.put("status", status);
        }
    }

    private static String first(JsonNode payload, String... keys) {
        for (String key : keys) {
            String text = payload.path(key).asText("");
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static void putText(ObjectNode object, String field, String value) {
        if (value == null || value.isBlank()) {
            if (!object.path(field).asText("").isBlank()) {
                return;
            }
            object.put(field, "");
            return;
        }
        object.put(field, value);
    }

    private JsonNode payload(long matchId) {
        LocalDate today = LocalDate.now(SHANGHAI);
        JsonNode found = null;
        for (int shift = -14; shift <= 7; shift++) {
            try {
                SportMatch row = matches.find(today.plusDays(shift), matchId);
                if (row == null || row.getPayload() == null || row.getPayload().isBlank()) {
                    continue;
                }
                JsonNode node = json.readTree(row.getPayload());
                found = node;
                if (!node.path("score").asText("").isEmpty()
                        || !node.path("hteam_name").asText("").isEmpty()
                        || !node.path("ateam_name").asText("").isEmpty()) {
                    return node;
                }
            } catch (Exception ignored) {
                // 这场不在赛程里，或暂时读不到
            }
        }
        return found;
    }
}
