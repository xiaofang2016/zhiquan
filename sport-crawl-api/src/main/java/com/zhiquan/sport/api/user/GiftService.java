package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.api.speak.SpeakService;
import com.zhiquan.sport.store.chat.ChatTranscript;
import com.zhiquan.sport.store.entity.SportUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GiftService {

    private final UserAuthService auth;
    private final MissionService mission;
    private final SpeakService speak;
    private final ObjectMapper json;
    private final RestTemplate http;
    private final String baseUrl;
    private volatile Cached cache = new Cached(Map.of(), 0);

    public GiftService(UserAuthService auth, MissionService mission, SpeakService speak, ObjectMapper json,
                       @Value("${sport.community.base-url:https://kzb001.net}") String baseUrl) {
        this.auth = auth;
        this.mission = mission;
        this.speak = speak;
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.http = new RestTemplate(factory);
    }

    public Map<String, Object> give(String token, String mid, Long giftId, Integer countRaw) {
        if (giftId == null || giftId < 1) {
            throw new IllegalArgumentException("没有这个礼物");
        }
        int count = countRaw == null ? 1 : countRaw;
        GiftCatalog.Gift gift = require(giftId);
        int cost = GiftCatalog.cost(gift.yinlang(), count);
        String content = GiftCatalog.line(gift.name(), count);
        SportUser user = auth.account(token);
        String matchId = ChatTranscript.matchId(mid);
        Map<String, Object> after = mission.adjust(token, GiftCatalog.title(gift.name(), count), -cost);
        try {
            speak.speak(matchId, SpeakService.nickOf(user), content);
        } catch (RuntimeException ex) {
            try {
                mission.adjust(token, "送礼退回", cost);
            } catch (RuntimeException ignored) {
                throw new IllegalArgumentException("送礼失败，请稍后再试");
            }
            if (ex instanceof IllegalArgumentException business) {
                throw business;
            }
            throw new IllegalArgumentException("送礼失败，音浪已退回");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", after.get("balance"));
        data.put("giftId", gift.id());
        data.put("giftName", gift.name());
        data.put("count", count);
        data.put("cost", cost);
        data.put("content", content);
        return data;
    }

    private GiftCatalog.Gift require(long id) {
        GiftCatalog.Gift gift = catalog().get(id);
        if (gift == null) {
            throw new IllegalArgumentException("没有这个礼物");
        }
        return gift;
    }

    private Map<Long, GiftCatalog.Gift> catalog() {
        Cached current = cache;
        long now = System.currentTimeMillis();
        if (current.until > now && !current.gifts.isEmpty()) {
            return current.gifts;
        }
        Map<Long, GiftCatalog.Gift> gifts = new LinkedHashMap<>();
        for (int type = 0; type <= 1; type++) {
            JsonNode body = get(baseUrl + "/prod-api/gift/getGiftList?GiftType=" + type);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.isArray()) {
                continue;
            }
            for (JsonNode item : data) {
                long id = item.path("id").asLong(0);
                if (id < 1 || gifts.containsKey(id)) {
                    continue;
                }
                try {
                    GiftCatalog.Gift gift = GiftCatalog.find(body, id);
                    if (gift != null) {
                        gifts.put(id, gift);
                    }
                } catch (IllegalArgumentException ignored) {
                    // 这一件没有价格，跳过，不影响目录里的其他礼物。
                }
            }
        }
        if (gifts.isEmpty()) {
            if (!current.gifts.isEmpty()) {
                return current.gifts;
            }
            throw new IllegalArgumentException("礼物暂时没有拿到");
        }
        cache = new Cached(Map.copyOf(gifts), now + 60_000);
        return gifts;
    }

    private JsonNode get(String url) {
        try {
            return http.execute(URI.create(url), HttpMethod.GET, request -> {
                request.getHeaders().set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
                request.getHeaders().set(HttpHeaders.REFERER, baseUrl + "/");
                request.getHeaders().setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            }, response -> {
                byte[] body = response.getBody().readNBytes(1_000_001);
                if (!response.getStatusCode().is2xxSuccessful() || body.length == 0 || body.length > 1_000_000) {
                    return null;
                }
                return json.readTree(body);
            });
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record Cached(Map<Long, GiftCatalog.Gift> gifts, long until) {
    }
}
