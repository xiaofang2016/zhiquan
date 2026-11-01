package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.chat.ZhiquanChatRows.GroupRow;
import com.zhiquan.sport.api.chat.ZhiquanChatRows.MessageRow;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试页读 zhiquan_chat。群列表只带每个群最新一条，点开再取最新 100 条。
 */
@Service
public class ZhiquanChatDebugService {

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public ZhiquanChatDebugService(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public GroupPage groups() {
        List<GroupRow> rows = new ArrayList<>();
        for (GroupRow group : loadGroups().values()) {
            MessageRow latest = latest(group.gid(), 1).stream().findFirst().orElse(null);
            rows.add(new GroupRow(group.id(), group.gid(), group.name(), group.ownerName(), group.updatedAt(), latest));
        }
        rows.sort(Comparator.comparingLong((GroupRow row) -> row.latest() == null ? 0 : row.latest().time()).reversed());
        return new GroupPage(aws.getZhiquanChatPartition(), rows);
    }

    public MessagePage messages(long groupId) {
        GroupRow group = loadGroups().get(groupId);
        if (group == null) {
            return new MessagePage(groupId, "", "", List.of());
        }
        return new MessagePage(group.id(), group.gid(), group.name(), latest(group.gid(), ZhiquanChatRows.MESSAGE_LIMIT));
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
        var response = dynamo.query(QueryRequest.builder()
                .tableName(aws.getZhiquanChatTable())
                .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(aws.getZhiquanChatPartition()),
                        ":prefix", AttributeValue.fromS("group#" + gid + "#msg#")))
                .scanIndexForward(false)
                .limit(limit)
                .build());
        List<MessageRow> rows = new ArrayList<>();
        List<Map<String, AttributeValue>> items = response.items();
        for (int i = items.size() - 1; i >= 0; i--) {
            rows.add(ZhiquanChatRows.message(items.get(i)));
        }
        return rows;
    }

    public record GroupPage(String partition, List<GroupRow> groups) {
    }

    public record MessagePage(long id, String gid, String name, List<MessageRow> messages) {
    }
}
