package com.zhiquan.sport.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class MallCrawlService {

    private final MallProperties properties;
    private final SportAwsProperties aws;
    private final UpstreamClient upstream;
    private final S3DatabaseStore objects;
    private final ObjectMapper json;
    private final ReentrantLock lock = new ReentrantLock();

    public MallCrawlService(MallProperties properties,
                            SportAwsProperties aws,
                            UpstreamClient upstream,
                            S3DatabaseStore objects,
                            ObjectMapper json) {
        this.properties = properties;
        this.aws = aws;
        this.upstream = upstream;
        this.objects = objects;
        this.json = json;
    }

    public void round() {
        if (!lock.tryLock()) {
            log.info("商城抓取正在进行，跳过这一轮");
            return;
        }
        try {
            ArrayNode categories = readList(upstream.get("/goods/category/list", Map.of("langtype", "zh")), "dataList");
            List<ObjectNode> goods = fetchAllGoods();
            if (goods.isEmpty()) {
                log.info("商城这一轮没有商品，保留上一份");
                return;
            }
            for (ObjectNode item : goods) {
                mergeDetail(item);
            }
            ObjectNode catalog = json.createObjectNode();
            catalog.put("fetchedAt", Instant.now().toString());
            catalog.set("categories", categories);
            ArrayNode list = catalog.putArray("goods");
            goods.forEach(list::add);
            objects.put(key("catalog.json"), json.writeValueAsBytes(catalog));
            log.info("商城抓取完成：分类 {}，商品 {}", categories.size(), goods.size());
        } catch (Exception ex) {
            log.warn("商城抓取失败: {}", ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private List<ObjectNode> fetchAllGoods() throws Exception {
        int size = Math.max(1, Math.min(properties.getPageSize(), 100));
        List<ObjectNode> out = new ArrayList<>();
        int page = 1;
        int totalPage = 1;
        while (page <= totalPage && page <= 50) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("type", "0");
            query.put("cid", "0");
            query.put("ishot", "-1");
            query.put("pn", String.valueOf(page));
            query.put("ps", String.valueOf(size));
            query.put("langtype", "zh");
            JsonNode root = json.readTree(upstream.get("/goods/list", query));
            JsonNode data = root.path("data");
            totalPage = Math.max(1, data.path("totalPage").asInt(1));
            ArrayNode pageList = readList(root, "dataList");
            for (JsonNode node : pageList) {
                if (node instanceof ObjectNode object && object.path("id").canConvertToLong()) {
                    out.add(object.deepCopy());
                }
            }
            if (pageList.isEmpty()) {
                break;
            }
            page++;
        }
        return out;
    }

    private void mergeDetail(ObjectNode item) {
        long id = item.path("id").asLong();
        try {
            JsonNode root = json.readTree(upstream.get("/goods/detail", Map.of(
                    "id", String.valueOf(id),
                    "langtype", "zh")));
            JsonNode detail = root.path("data");
            if (!detail.path("id").canConvertToLong()) {
                return;
            }
            copyText(item, detail, "description");
            copyText(item, detail, "introduction");
            copyText(item, detail, "detail");
            copyText(item, detail, "img1");
            copyText(item, detail, "img2");
            copyText(item, detail, "img3");
            if (detail.path("cover_url").isTextual() && !detail.path("cover_url").asText("").isBlank()) {
                item.put("cover_url", detail.path("cover_url").asText());
            }
            if (detail.path("amount").isNumber()) {
                item.put("amount", detail.path("amount").asDouble());
            }
            if (detail.path("stock").canConvertToInt()) {
                item.put("stock", detail.path("stock").asInt());
            }
            if (detail.path("sales").canConvertToInt()) {
                item.put("sales", detail.path("sales").asInt());
            }
        } catch (Exception ex) {
            log.warn("商城商品 {} 详情没有拉到: {}", id, ex.getMessage());
        }
    }

    private static void copyText(ObjectNode target, JsonNode source, String field) {
        if (source.path(field).isTextual()) {
            target.put(field, source.path(field).asText());
        }
    }

    private ArrayNode readList(byte[] body, String field) throws Exception {
        return readList(json.readTree(body), field);
    }

    private ArrayNode readList(JsonNode root, String field) {
        JsonNode list = root.path("data").path(field);
        if (list instanceof ArrayNode array) {
            return array;
        }
        return json.createArrayNode();
    }

    private String key(String name) {
        String prefix = aws.getMallPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "mall/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
