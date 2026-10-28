package com.zhiquan.sport.store.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.db.DatabaseDoc;
import com.zhiquan.sport.store.db.DatabaseTime;
import com.zhiquan.sport.store.db.DatabaseTier;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 进度跟正文放在同一个桶。现有权限可以写这个桶，建不了新的 DynamoDB 表。
 * 正文是 {@code database/...json}，进度是旁边的 {@code .meta}，按需队列在 {@code database/_queue/}。
 */
@Repository
public class DatabaseDocRepository {

    private final S3Client s3;
    private final SportAwsProperties aws;
    private final ObjectMapper json;

    public DatabaseDocRepository(S3Client s3, SportAwsProperties aws, ObjectMapper json) {
        this.s3 = s3;
        this.aws = aws;
        this.json = json;
    }

    public void ensureTable() {
        s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(aws.getImageBucket())
                .prefix(prefix())
                .maxKeys(1)
                .build());
    }

    public DatabaseDoc find(String key) {
        return read(metaKey(key), key);
    }

    public void save(String key, String sha256, String tier, int bytes, String path, String query, Instant fetchedAt) {
        ObjectNode node = json.createObjectNode();
        node.put("sha256", sha256);
        node.put("tier", tier);
        node.put("bytes", bytes);
        node.put("fetchedAt", DatabaseTime.format(fetchedAt));
        node.put("path", path == null ? "" : path);
        node.put("query", query == null ? "" : query);
        put(metaKey(key), node);
        if (DatabaseTier.CACHED.equals(tier) || DatabaseTier.DAILY.equals(tier)
                || DatabaseTier.LIVE.equals(tier) || DatabaseTier.FROZEN.equals(tier)) {
            delete(queueKey(key));
        }
    }

    public void touch(String key, String tier, Instant fetchedAt) {
        DatabaseDoc existing = find(key);
        if (existing == null || existing.sha256() == null) {
            return;
        }
        save(key, existing.sha256(), tier, 0, existing.path(), existing.query(), fetchedAt);
    }

    public void enqueue(String key, String path, String query, Instant dueAt) {
        ObjectNode node = json.createObjectNode();
        node.put("key", key);
        node.put("path", path == null ? "" : path);
        node.put("query", query == null ? "" : query);
        node.put("dueAt", DatabaseTime.format(dueAt));
        put(queueKey(key), node);
    }

    public void discard(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        delete(queueKey(key));
    }

    public void postpone(String key, Instant dueAt) {
        DatabaseDoc queued = read(queueKey(key), key);
        String path = queued == null ? "" : queued.path();
        String query = queued == null ? "" : queued.query();
        enqueue(key, path, query, dueAt);
    }

    public List<DatabaseDoc> dueDemands(Instant now, int limit) {
        List<DatabaseDoc> due = new ArrayList<>();
        String token = null;
        int guard = 0;
        while (due.size() < limit && guard++ < 20) {
            ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                    .bucket(aws.getImageBucket())
                    .prefix(prefix() + "_queue/")
                    .maxKeys(100);
            if (token != null) {
                request.continuationToken(token);
            }
            var response = s3.listObjectsV2(request.build());
            for (var object : response.contents()) {
                DatabaseDoc doc = read(object.key(), null);
                if (doc == null || doc.dueAt() == null || doc.dueAt().compareTo(DatabaseTime.format(now)) > 0) {
                    continue;
                }
                due.add(doc);
                if (due.size() == limit) {
                    return due;
                }
            }
            if (!response.isTruncated()) {
                break;
            }
            token = response.nextContinuationToken();
        }
        return due;
    }

    public int historyCursor() {
        DatabaseDoc doc = read(prefix() + "_job/history", "job#history");
        if (doc == null || doc.sha256() == null || doc.sha256().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(doc.sha256());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public void historyCursor(int cursor) {
        ObjectNode node = json.createObjectNode();
        node.put("sha256", Integer.toString(cursor));
        put(prefix() + "_job/history", node);
    }

    private DatabaseDoc read(String objectKey, String fallbackKey) {
        try {
            byte[] body = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(aws.getImageBucket())
                    .key(objectKey)
                    .build()).asByteArray();
            JsonNode node = json.readTree(body);
            String key = text(node, "key");
            if (key == null || key.isBlank()) {
                key = fallbackKey;
            }
            return new DatabaseDoc(key, text(node, "sha256"), text(node, "tier"), text(node, "fetchedAt"),
                    text(node, "dueAt"), text(node, "path"), text(node, "query"));
        } catch (NoSuchKeyException ex) {
            return null;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return null;
            }
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("资料库进度读不出来 " + objectKey, ex);
        }
    }

    private void put(String objectKey, ObjectNode node) {
        byte[] body;
        try {
            body = json.writeValueAsBytes(node);
        } catch (Exception ex) {
            throw new IllegalStateException("资料库进度写不进去", ex);
        }
        s3.putObject(PutObjectRequest.builder()
                .bucket(aws.getImageBucket())
                .key(objectKey)
                .contentType("application/json")
                .contentLength((long) body.length)
                .build(), RequestBody.fromBytes(body));
    }

    private void delete(String objectKey) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(aws.getImageBucket()).key(objectKey).build());
    }

    private String prefix() {
        String prefix = aws.getDbPrefix() == null || aws.getDbPrefix().isBlank() ? "database/" : aws.getDbPrefix();
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String metaKey(String key) {
        return key + ".meta";
    }

    private String queueKey(String key) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return prefix() + "_queue/" + encoded;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
