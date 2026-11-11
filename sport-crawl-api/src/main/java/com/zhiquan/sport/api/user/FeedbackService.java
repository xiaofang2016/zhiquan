package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private static final int KEEP = 20;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final ObjectMapper json;

    public FeedbackService(UserAuthService auth, SportUserRepository users, ObjectMapper json) {
        this.auth = auth;
        this.users = users;
        this.json = json;
    }

    public Map<String, Object> add(String token, String content, String contact, List<String> images) {
        SportUser user = auth.account(token);
        String body = content == null ? "" : content.trim();
        if (body.isEmpty() || body.length() > 1000) {
            throw new IllegalArgumentException("反馈内容不正确");
        }
        String phone = user.getPhone();
        for (int attempt = 0; attempt < 2; attempt++) {
            String raw = users.feedback(phone);
            ArrayNode rows = read(raw);
            ObjectNode row = rows.insertObject(0);
            row.put("content", body);
            row.put("contact", contact == null ? "" : cut(contact.trim(), 64));
            ArrayNode pics = row.putArray("images");
            if (images != null) {
                int kept = 0;
                for (String image : images) {
                    if (image == null || image.isBlank() || kept >= 3) {
                        continue;
                    }
                    pics.add(cut(image.trim(), 500));
                    kept++;
                }
            }
            row.put("time", LocalDateTime.now(MissionRules.ZONE).format(STAMP));
            while (rows.size() > KEEP) {
                rows.remove(rows.size() - 1);
            }
            try {
                users.saveFeedback(phone, raw == null ? "" : raw, rows.toString());
                return view(row);
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    throw new IllegalArgumentException("请稍后再试");
                }
            }
        }
        throw new IllegalArgumentException("请稍后再试");
    }

    public Map<String, Object> list(String token) {
        SportUser user = auth.account(token);
        ArrayNode rows = read(users.feedback(user.getPhone()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode row : rows) {
            if (row instanceof ObjectNode node) {
                items.add(view(node));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", items);
        return data;
    }

    private ArrayNode read(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                JsonNode node = json.readTree(raw);
                if (node instanceof ArrayNode rows) {
                    return rows;
                }
            } catch (Exception ignored) {
                // 坏数据按空列表重写
            }
        }
        return json.createArrayNode();
    }

    private static Map<String, Object> view(ObjectNode row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("content", row.path("content").asText(""));
        item.put("contact", row.path("contact").asText(""));
        List<String> images = new ArrayList<>();
        for (JsonNode image : row.path("images")) {
            if (image.isTextual() && !image.asText().isBlank()) {
                images.add(image.asText());
            }
        }
        item.put("images", images);
        item.put("time", row.path("time").asText(""));
        return item;
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
