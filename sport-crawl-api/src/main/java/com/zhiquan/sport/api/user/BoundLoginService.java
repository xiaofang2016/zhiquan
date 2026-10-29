package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BoundLoginService {

    private final SportUserRepository users;
    private final JxtClient jxt;
    private final ObjectMapper json;

    public BoundLoginService(SportUserRepository users, JxtClient jxt, ObjectMapper json) {
        this.users = users;
        this.jxt = jxt;
        this.json = json;
    }

    public Map<String, String> open(String raw) {
        if (raw == null || raw.length() > 2048) {
            throw new IllegalArgumentException("签名不正确");
        }
        JsonNode node;
        try {
            node = json.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("签名不正确");
        }
        String userId = node.path("userId").asText("");
        String phone = node.path("phone").asText("");
        String brand = node.path("brand").asText("");
        long now = Instant.now().getEpochSecond();
        try {
            BoundLogin.check(jxt.outboundSecret(), node.path("timestamp").asText(""), node.path("nonce").asText(""),
                    userId, phone, brand, node.path("signature").asText(""), now);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("签名不正确");
        }
        SportUser user = users.find(phone).orElse(null);
        if (user == null || !userId.equals(user.getUserId())) {
            throw new IllegalArgumentException(BoundLogin.token(false, "", 0, now, brand));
        }
        boolean bound = "ued".equals(brand) ? user.isUedBound() : user.isJxtBound();
        String access = "ued".equals(brand) ? user.getUedAccessToken() : user.getJxtAccessToken();
        long expire = "ued".equals(brand) ? user.getUedTokenExpire() : user.getJxtTokenExpire();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("accessToken", BoundLogin.token(bound, access, expire, now, brand));
        return data;
    }
}
