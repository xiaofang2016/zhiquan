package com.zhiquan.sport.store.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.chat.ChatDocument;
import com.zhiquan.sport.store.chat.ChatMessage;
import com.zhiquan.sport.store.chat.ChatTranscript;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;

/**
 * 读取是一次 GET。更新是读出文档、按版本追加、再用对象的 ETag 条件写回。
 * 有人同时写入时条件写失败，重新读再追加，不会把另一边的消息盖掉。
 */
@Repository
public class S3ChatStore {

    private static final int ATTEMPTS = 5;

    private final S3Client s3;
    private final SportAwsProperties aws;
    private final ObjectMapper json = new ObjectMapper();

    public S3ChatStore(S3Client s3, SportAwsProperties aws) {
        this.s3 = s3;
        this.aws = aws;
    }

    public ChatDocument read(String matchId) {
        return load(ChatTranscript.matchId(matchId)).document();
    }

    public ChatDocument append(String matchId, String roomId, List<ChatMessage> incoming) {
        String id = ChatTranscript.matchId(matchId);
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Loaded loaded = load(id);
            ChatTranscript.Merge merged = ChatTranscript.append(loaded.document(), roomId, incoming);
            if (!merged.changed()) {
                return merged.document();
            }
            try {
                put(id, merged.document(), loaded.etag());
                return merged.document();
            } catch (IOException ex) {
                throw new IllegalStateException("聊天文档无法写入", ex);
            } catch (S3Exception ex) {
                if (ex.statusCode() != 412) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("聊天文档正在被同时更新");
    }

    private Loaded load(String matchId) {
        try {
            var bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(aws.getImageBucket())
                    .key(objectKey(matchId))
                    .build());
            ChatDocument document = json.readValue(bytes.asByteArray(), ChatDocument.class);
            if (document.messages() == null) {
                document = new ChatDocument(matchId, document.roomId(), document.version(), List.of());
            }
            return new Loaded(document, bytes.response().eTag());
        } catch (NoSuchKeyException ex) {
            return new Loaded(ChatDocument.empty(matchId), null);
        } catch (IOException ex) {
            throw new IllegalStateException("聊天文档无法读取", ex);
        }
    }

    private void put(String matchId, ChatDocument document, String etag) throws IOException {
        byte[] body = json.writeValueAsBytes(document);
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(aws.getImageBucket())
                .key(objectKey(matchId))
                .contentType("application/json")
                .contentLength((long) body.length);
        if (etag == null || etag.isBlank()) {
            request.ifNoneMatch("*");
        } else {
            request.ifMatch(etag);
        }
        s3.putObject(request.build(), RequestBody.fromBytes(body));
    }

    private String objectKey(String matchId) {
        String prefix = aws.getChatPrefix() == null ? "chat/" : aws.getChatPrefix();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + matchId + ".json";
    }

    private record Loaded(ChatDocument document, String etag) {
    }
}
