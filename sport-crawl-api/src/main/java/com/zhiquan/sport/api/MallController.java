package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MallController {

    private final S3DatabaseStore objects;
    private final SportAwsProperties aws;
    private final ObjectMapper json;

    public MallController(S3DatabaseStore objects, SportAwsProperties aws, ObjectMapper json) {
        this.objects = objects;
        this.aws = aws;
        this.json = json;
    }

    @GetMapping("/prod-api/goods/category/list")
    public KzbResult categories(@RequestParam(required = false) String langtype) {
        JsonNode categories = catalog().path("categories");
        ObjectNode data = json.createObjectNode();
        data.set("dataList", categories.isArray() ? categories.deepCopy() : json.createArrayNode());
        return KzbResult.ok(data);
    }

    @GetMapping("/prod-api/goods/list")
    public KzbResult list(@RequestParam(required = false, defaultValue = "0") int type,
                          @RequestParam(required = false, defaultValue = "0") int cid,
                          @RequestParam(required = false, defaultValue = "-1") int ishot,
                          @RequestParam(required = false, defaultValue = "1") int pn,
                          @RequestParam(required = false, defaultValue = "10") int ps,
                          @RequestParam(required = false) String langtype) {
        int page = pn < 1 ? 1 : Math.min(pn, 500);
        int size = ps < 1 ? 10 : Math.min(ps, 100);
        JsonNode stored = catalog();
        ArrayNode all = filter(stored.path("goods"), type, cid, ishot);
        int total = all.size();
        int totalPage = Math.max(1, (total + size - 1) / size);
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        ArrayNode pageList = json.createArrayNode();
        for (int i = from; i < to; i++) {
            pageList.add(card(all.get(i)));
        }
        ObjectNode data = json.createObjectNode();
        data.put("total", total);
        data.put("totalPage", totalPage);
        data.put("currentPage", page);
        data.set("dataList", pageList);
        return KzbResult.ok(data);
    }

    @GetMapping("/prod-api/goods/detail")
    public KzbResult detail(@RequestParam long id, @RequestParam(required = false) String langtype) {
        for (JsonNode item : catalog().path("goods")) {
            if (item.path("id").asLong() == id) {
                return KzbResult.ok(item.deepCopy());
            }
        }
        return KzbResult.ok(null);
    }

    private ArrayNode filter(JsonNode goods, int type, int cid, int ishot) {
        ArrayNode out = json.createArrayNode();
        if (!goods.isArray()) {
            return out;
        }
        for (JsonNode item : goods) {
            if (!item.path("id").canConvertToLong()) {
                continue;
            }
            if (item.path("status").isNumber() && item.path("status").asInt() != 1) {
                continue;
            }
            if (cid > 0 && item.path("cid").asInt() != cid) {
                continue;
            }
            if (type > 0 && item.path("type").asInt() != type) {
                continue;
            }
            if (ishot >= 0 && item.path("ishot").asInt() != ishot) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private ObjectNode card(JsonNode item) {
        ObjectNode card = json.createObjectNode();
        copy(card, item, "id");
        copy(card, item, "title");
        copy(card, item, "amount");
        copy(card, item, "cid");
        copy(card, item, "cname");
        copy(card, item, "cover_url");
        copy(card, item, "goods_rule_id");
        copy(card, item, "ishot");
        copy(card, item, "limit_type");
        copy(card, item, "sales");
        copy(card, item, "status");
        copy(card, item, "stock");
        copy(card, item, "type");
        return card;
    }

    private static void copy(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            target.set(field, value);
        }
    }

    private JsonNode catalog() {
        try {
            byte[] body = objects.get(key("catalog.json"));
            if (body == null || body.length == 0) {
                return json.createObjectNode();
            }
            return json.readTree(body);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    private String key(String name) {
        String prefix = aws.getMallPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "mall/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
