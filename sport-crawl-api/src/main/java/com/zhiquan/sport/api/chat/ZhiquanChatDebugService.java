package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.chat.ZhiquanChatRows.GroupRow;
import com.zhiquan.sport.api.chat.ZhiquanChatRows.MessageRow;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试页读 zhiquan_chat。群列表只带每个群最新一条，点开再取最新 100 条。
 */
@Service
public class ZhiquanChatDebugService {

    private static final long IMAGE_BASE_TTL_MS = 30 * 60_000L;

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;
    private volatile String imageBase = "";
    private volatile long imageBaseAt;

    public ZhiquanChatDebugService(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public GroupPage groups() {
        List<GroupRow> rows = new ArrayList<>();
        for (GroupRow group : loadGroups().values()) {
            MessageRow latest = latest(group.gid(), 1).stream().findFirst().orElse(null);
            rows.add(new GroupRow(group.id(), group.gid(), group.name(), group.ownerName(), group.updatedAt(), group.notice(), group.avatar(), latest));
        }
        rows.sort(Comparator.comparingLong((GroupRow row) -> row.latest() == null ? 0 : row.latest().time()).reversed());
        return new GroupPage(aws.getZhiquanChatPartition(), rows, imageBase());
    }

    public MessagePage messages(long groupId) {
        GroupRow group = loadGroups().get(groupId);
        if (group == null) {
            return new MessagePage(groupId, "", "", List.of(), "");
        }
        return new MessagePage(group.id(), group.gid(), group.name(), latest(group.gid(), ZhiquanChatRows.MESSAGE_LIMIT), imageBase());
    }

    private Map<Long, GroupRow> loadGroups() {
        Map<String, AttributeValue> values = Map.of(
                ":pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                ":prefix", AttributeValue.fromS("group#"),
                ":kind", AttributeValue.fromS("group"));
        Map<Long, GroupRow> groups = new LinkedHashMap<>();
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
                    groups.put(row.id(), row);
                }
            }
            start = response.hasLastEvaluatedKey() && !response.lastEvaluatedKey().isEmpty()
                    ? response.lastEvaluatedKey() : null;
        } while (start != null);
        return groups;
    }

    private List<MessageRow> latest(String gid, int limit) {
        int cap = limit <= 1 ? 40 : 1600;
        List<MessageRow> newest = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        int scanned = 0;
        while (newest.size() < limit && scanned < cap) {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(aws.getZhiquanChatTable())
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                            ":prefix", AttributeValue.fromS("group#" + gid + "#msg#")))
                    .scanIndexForward(false)
                    .limit(Math.min(100, cap - scanned));
            if (start != null) {
                builder.exclusiveStartKey(start);
            }
            var response = dynamo.query(builder.build());
            List<Map<String, AttributeValue>> items = response.items();
            if (items.isEmpty()) {
                break;
            }
            scanned += items.size();
            for (Map<String, AttributeValue> item : items) {
                MessageRow row = ZhiquanChatRows.message(item);
                if (row.content().isBlank() || ZhiquanChatRows.isClaimNotice(row.content())) {
                    continue;
                }
                newest.add(row);
                if (newest.size() >= limit) {
                    break;
                }
            }
            if (!response.hasLastEvaluatedKey() || response.lastEvaluatedKey().isEmpty()) {
                break;
            }
            start = response.lastEvaluatedKey();
        }
        Collections.reverse(newest);
        return newest;
    }

    private String imageBase() {
        long now = System.currentTimeMillis();
        if (!imageBase.isBlank() && now - imageBaseAt < IMAGE_BASE_TTL_MS) {
            return imageBase;
        }
        String fetched = ZhiquanImageBase.fetch(imBase());
        if (!fetched.isBlank()) {
            imageBase = fetched;
            imageBaseAt = now;
            return fetched;
        }
        return imageBase.isBlank() ? ZhiquanImageBase.FALLBACK : imageBase;
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

    public record MessagePage(long id, String gid, String name, List<MessageRow> messages, String imageBase) {
    }
}
