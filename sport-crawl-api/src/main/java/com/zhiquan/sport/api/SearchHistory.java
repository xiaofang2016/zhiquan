package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Map;

/** 说球帝账号自己的搜索词。热词仍用上游。 */
@Service
public class SearchHistory {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final ObjectMapper json;

    public SearchHistory(UserAuthService auth, SportUserRepository users, ObjectMapper json) {
        this.auth = auth;
        this.users = users;
        this.json = json;
    }

    public List<String> keys(String token) {
        SportUser user = account(token);
        if (user == null) {
            return List.of();
        }
        return SearchPages.keys(users.profileJson(user.getPhone(), "search"), json);
    }

    public void remember(String query) {
        String word = SearchPages.word(SearchPages.param(query, "key"));
        SportUser user = account(SearchPages.param(query, "token"));
        if (user == null || word.isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String raw = users.profileJson(user.getPhone(), "search");
            List<String> next = SearchPages.remember(SearchPages.keys(raw, json), word);
            try {
                users.saveSearch(user.getPhone(), raw, encode(next));
                return;
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    return;
                }
            } catch (RuntimeException ex) {
                return;
            }
        }
    }

    public void clear(String token) {
        SportUser user = account(token);
        if (user == null) {
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String raw = users.profileJson(user.getPhone(), "search");
            try {
                users.saveSearch(user.getPhone(), raw, encode(List.of()));
                return;
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    return;
                }
            }
        }
    }

    public byte[] local(List<String> keys) {
        ObjectNode root = json.createObjectNode();
        root.put("msg", "ok");
        root.put("code", "0");
        ObjectNode data = root.putObject("data");
        data.putArray("hotSearchHistory");
        writeKeys(data.putArray("userSearchHistory"), keys);
        try {
            return json.writeValueAsBytes(root);
        } catch (Exception ex) {
            return "{\"msg\":\"ok\",\"code\":\"0\",\"data\":{\"hotSearchHistory\":[],\"userSearchHistory\":[]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public byte[] overlay(byte[] upstream, List<String> keys) {
        try {
            ObjectNode root = (ObjectNode) json.readTree(upstream);
            if (!root.path("data").isObject()) {
                return upstream;
            }
            writeKeys(((ObjectNode) root.path("data")).putArray("userSearchHistory"), keys);
            return json.writeValueAsBytes(root);
        } catch (Exception ex) {
            return upstream;
        }
    }

    private static void writeKeys(ArrayNode list, List<String> keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            list.addObject().put("key", key);
        }
    }

    private String encode(List<String> keys) {
        try {
            return json.writeValueAsString(Map.of("keys", keys));
        } catch (Exception ex) {
            return "{\"keys\":[]}";
        }
    }

    private SportUser account(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return auth.account(token);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
