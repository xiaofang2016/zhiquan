package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 读出正文和 ETag。写回时带上这份 ETag，别人先写过就失败，调用方再读再并。
 */
@Repository
public class S3JsonStore {

    private final S3Client s3;
    private final SportAwsProperties aws;

    public S3JsonStore(S3Client s3, SportAwsProperties aws) {
        this.s3 = s3;
        this.aws = aws;
    }

    public Loaded load(String key) {
        try {
            var bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(aws.getImageBucket())
                    .key(key)
                    .build());
            return new Loaded(bytes.asByteArray(), bytes.response().eTag());
        } catch (NoSuchKeyException ex) {
            return new Loaded(null, null);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return new Loaded(null, null);
            }
            throw ex;
        }
    }

    public void store(String key, byte[] body, String etag) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(aws.getImageBucket())
                .key(key)
                .contentType("application/json")
                .contentLength((long) body.length);
        if (etag == null || etag.isBlank()) {
            request.ifNoneMatch("*");
        } else {
            request.ifMatch(etag);
        }
        s3.putObject(request.build(), RequestBody.fromBytes(body));
    }

    public record Loaded(byte[] body, String etag) {
    }
}
