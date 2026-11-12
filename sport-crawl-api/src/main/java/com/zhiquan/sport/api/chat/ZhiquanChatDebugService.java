package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.chat.ZhiquanChatRows.GroupRow;
import com.zhiquan.sport.api.chat.ZhiquanChatRows.MessageRow;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 调试页读 zhiquan_chat。群列表只带每个群最新一条，点开再取最新 100 条。
 */
@Service
public class ZhiquanChatDebugService {

    private static final Logger log = LoggerFactory.getLogger(ZhiquanChatDebugService.class);
    private static final long IMAGE_BASE_TTL_MS = 30 * 60_000L;

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;
    private final ExecutorService groupRefresh = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "zhiquan-chat-groups");
        thread.setDaemon(true);
        return thread;
    });
    private final GroupCatalog groups = new GroupCatalog(GroupCatalog.TTL_MS, groupRefresh);
    private volatile String imageBase = "";
    private volatile long imageBaseAt;

    public ZhiquanChatDebugService(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
        groups.prefetch(System.currentTimeMillis(), this::queryGroups);
    }

    public GroupPage groups() {
        List<GroupRow> rows = new ArrayList<>();
        for (GroupRow group : loadGroups().values()) {
            MessageRow latest = latest(group.gid(), 1).stream().findFirst().orElse(null);
            rows.add(new GroupRow(group.id(), group.gid(), group.name(), group.ownerName(), group.updatedAt(),
                    group.notice(), group.avatar(), latest, group.memberCount(), group.muted(), group.ownerId(),
                    group.noticeStyle(), group.createdTime()));
        }
        rows.sort(Comparator.comparingLong((GroupRow row) -> row.latest() == null ? 0 : row.latest().time()).reversed());
        return new GroupPage(aws.getZhiquanChatPartition(), rows, imageBase());
    }

    public Map<String, AttributeValue> messageItem(long groupId, String id) {
        GroupRow group = loadGroups().get(groupId);
        if (group == null || !ZhiquanChatRows.olderCursor(group.gid(), id)) {
            throw new IllegalArgumentException("没有这个红包");
        }
        var response = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getZhiquanChatTable())
                .key(Map.of(
                        "pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                        "sk", AttributeValue.fromS(id)))
                .projectionExpression("content")
                .build());
        if (!response.hasItem() || response.item().isEmpty()) {
            throw new IllegalArgumentException("没有这个红包");
        }
        return response.item();
    }

    public MessagePage messages(long groupId, String before) {
        GroupRow group = loadGroups().get(groupId);
        if (group == null) {
            return new MessagePage(groupId, "", "", List.of(), "", "");
        }
        MessageSlice slice = page(group.gid(), ZhiquanChatRows.MESSAGE_LIMIT, before);
        return new MessagePage(group.id(), group.gid(), group.name(), slice.messages(), imageBase(), slice.older());
    }

    private Map<Long, GroupRow> loadGroups() {
        return groups.get(System.currentTimeMillis(), this::queryGroups);
    }

    private Map<Long, GroupRow> queryGroups() {
        try {
            return readGroups();
        } catch (RuntimeException ex) {
            log.warn("读取职圈群列表失败: {}", ex.toString());
            throw ex;
        }
    }

    private Map<Long, GroupRow> readGroups() {
        Map<String, AttributeValue> values = Map.of(
                ":pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                ":prefix", AttributeValue.fromS("group#"),
                ":kind", AttributeValue.fromS("group"));
        Map<Long, GroupRow> found = new LinkedHashMap<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(aws.getZhiquanChatTable())
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .filterExpression("kind = :kind")
                    .expressionAttributeValues(values);
            if (start != null) {
                builder.exclusiveStartKey(start);
            }
            var response = dynamo.query(builder.build());
            for (Map<String, AttributeValue> item : response.items()) {
                GroupRow row = ZhiquanChatRows.group(item);
                if (row != null) {
                    found.put(row.id(), row);
                }
            }
            start = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey() : null;
        } while (start != null);
        return found;
    }

    private List<MessageRow> latest(String gid, int limit) {
        return page(gid, limit, null).messages();
    }

    /**
     * 从新到旧取一页。before 是上一页最老一条的排序键，没有就从最新开始。
     * 领取提示跳过，但不算进这一页的条数。扫到头了 older 为空。
     */
    private MessageSlice page(String gid, int limit, String before) {
        int cap = limit <= 1 ? 40 : 1600;
        String prefix = "group#" + gid + "#msg#";
        boolean ranged = ZhiquanChatRows.olderCursor(gid, before);
        List<MessageRow> newest = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        String boundary = "";
        boolean more = false;
        int scanned = 0;
        while (newest.size() < limit && scanned < cap) {
            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":pk", AttributeValue.fromS(aws.getZhiquanChatPartition()));
            String keyExpr;
            if (ranged) {
                keyExpr = "pk = :pk AND sk BETWEEN :start AND :end";
                values.put(":start", AttributeValue.fromS(prefix));
                values.put(":end", AttributeValue.fromS(before));
            } else {
                keyExpr = "pk = :pk AND begins_with(sk, :prefix)";
                values.put(":prefix", AttributeValue.fromS(prefix));
            }
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(aws.getZhiquanChatTable())
                    .keyConditionExpression(keyExpr)
                    .expressionAttributeValues(values)
                    .scanIndexForward(false)
                    .limit(Math.min(100, cap - scanned));
            if (start != null) {
                builder.exclusiveStartKey(start);
            }
            var response = dynamo.query(builder.build());
            List<Map<String, AttributeValue>> items = response.items();
            if (items.isEmpty()) {
                more = false;
                break;
            }
            scanned += items.size();
            boolean filled = false;
            for (int i = 0; i < items.size(); i++) {
                Map<String, AttributeValue> item = items.get(i);
                AttributeValue skValue = item.get("sk");
                String sk = skValue == null || skValue.s() == null ? "" : skValue.s();
                if (ranged && !sk.isEmpty() && sk.compareTo(before) >= 0) {
                    continue;
                }
                if (!sk.isEmpty()) {
                    boundary = sk;
                }
                MessageRow row = ZhiquanChatRows.message(item);
                if (row.content().isBlank() || ZhiquanChatRows.isClaimNotice(row.content())) {
                    continue;
                }
                newest.add(row);
                if (newest.size() >= limit) {
                    boolean tail = i == items.size() - 1;
                    boolean continued = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty();
                    more = !tail || continued;
                    filled = true;
                    break;
                }
            }
            if (filled) {
                break;
            }
            if (!response.hasLastEvaluatedKey() || response.lastEvaluatedKey().isEmpty()) {
                more = false;
                break;
            }
            start = response.lastEvaluatedKey();
            if (scanned >= cap) {
                more = true;
                break;
            }
        }
        Collections.reverse(newest);
        return new MessageSlice(newest, more && !boundary.isEmpty() ? boundary : "");
    }

    private String imageBase() {
        long now = System.currentTimeMillis();
        if (usable(imageBase) && now - imageBaseAt < IMAGE_BASE_TTL_MS) {
            return imageBase;
        }
        String fetched = ZhiquanImageBase.fetch(imBase());
        if (usable(fetched)) {
            imageBase = fetched;
            imageBaseAt = now;
            return fetched;
        }
        return usable(imageBase) ? imageBase : ZhiquanImageBase.FALLBACK;
    }

    private static boolean usable(String host) {
        return host != null && !host.isBlank() && !host.contains("oss.imnono.net");
    }

    private String imBase() {
        try {
            var response = dynamo.getItem(GetItemRequest.builder()
                    .tableName(aws.getZhiquanChatTable())
                    .key(Map.of(
                            "pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                            "sk", AttributeValue.fromS("meta")))
                    .projectionExpression("imBase")
                    .build());
            if (response.hasItem()) {
                AttributeValue value = response.item().get("imBase");
                if (value != null && value.s() != null && !value.s().isBlank()) {
                    return value.s().trim();
                }
            }
        } catch (Exception ignored) {
            // 图片域名拿不到时用默认地址，消息列表照常返回。
        }
        return "https://anobs9bb.cyou";
    }

    public record GroupPage(String partition, List<GroupRow> groups, String imageBase) {
    }

    public record MessagePage(long id, String gid, String name, List<MessageRow> messages, String imageBase, String older) {
    }

    private record MessageSlice(List<MessageRow> messages, String older) {
    }
}
