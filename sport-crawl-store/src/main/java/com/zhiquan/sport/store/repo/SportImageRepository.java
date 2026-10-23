package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportImage;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Repository
public class SportImageRepository {

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public SportImageRepository(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public void upsert(SportImage row) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("source_hash", Attrs.s(row.getSourceHash()));
        item.put("source_url", Attrs.s(row.getSourceUrl()));
        putText(item, "content_type", row.getContentType());
        item.put("byte_size", Attrs.n(row.getByteSize() == null ? 0 : row.getByteSize()));
        putText(item, "relative_path", row.getRelativePath());
        item.put("status", Attrs.s(row.getStatus()));
        putText(item, "error_message", row.getErrorMessage());
        item.put("updated_at", Attrs.s(Attrs.now()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getImageTable()).item(item).build());
    }

    public SportImage selectByHash(String hash) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getImageTable())
                .key(Map.of("source_hash", Attrs.s(hash)))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return null;
        }
        return toImage(item);
    }

    public List<SportImage> selectByHashes(Collection<String> hashes) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String hash : hashes) {
            if (hash != null && !hash.isBlank()) {
                unique.add(hash);
            }
        }
        List<String> pending = new ArrayList<>(unique);
        List<SportImage> found = new ArrayList<>();
        String table = aws.getImageTable();
        for (int offset = 0; offset < pending.size(); offset += 100) {
            List<String> chunk = pending.subList(offset, Math.min(offset + 100, pending.size()));
            List<Map<String, AttributeValue>> keys = new ArrayList<>();
            for (String hash : chunk) {
                keys.add(Map.of("source_hash", Attrs.s(hash)));
            }
            Map<String, KeysAndAttributes> request = Map.of(table, KeysAndAttributes.builder().keys(keys).build());
            int guard = 0;
            while (!request.isEmpty() && guard++ < 5) {
                BatchGetItemResponse response = dynamo.batchGetItem(BatchGetItemRequest.builder()
                        .requestItems(request)
                        .build());
                for (Map<String, AttributeValue> item : response.responses().getOrDefault(table, List.of())) {
                    found.add(toImage(item));
                }
                request = response.unprocessedKeys();
            }
        }
        return found;
    }

    public List<SportImage> selectOk() {
        List<SportImage> found = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            ScanRequest.Builder request = ScanRequest.builder()
                    .tableName(aws.getImageTable())
                    .filterExpression("#status = :ok")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(":ok", Attrs.s("ok")));
            if (start != null) {
                request.exclusiveStartKey(start);
            }
            ScanResponse response = dynamo.scan(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                found.add(toImage(item));
            }
            start = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (start != null);
        return found;
    }

    private static void putText(Map<String, AttributeValue> item, String name, String value) {
        if (value != null && !value.isBlank()) {
            item.put(name, Attrs.s(value));
        }
    }

    private static SportImage toImage(Map<String, AttributeValue> item) {
        SportImage row = new SportImage();
        row.setSourceHash(Attrs.str(item, "source_hash"));
        row.setSourceUrl(Attrs.str(item, "source_url"));
        row.setContentType(Attrs.str(item, "content_type"));
        row.setByteSize(Attrs.num(item, "byte_size"));
        row.setRelativePath(Attrs.str(item, "relative_path"));
        row.setStatus(Attrs.str(item, "status"));
        row.setErrorMessage(Attrs.str(item, "error_message"));
        row.setUpdatedAt(Attrs.time(Attrs.str(item, "updated_at")));
        return row;
    }
}
