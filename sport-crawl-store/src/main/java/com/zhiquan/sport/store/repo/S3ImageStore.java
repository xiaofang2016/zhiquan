package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Repository
public class S3ImageStore {

    private final S3Client s3;
    private final SportAwsProperties aws;

    public S3ImageStore(S3Client s3, SportAwsProperties aws) {
        this.s3 = s3;
        this.aws = aws;
    }

    public void put(String relativePath, byte[] body, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(aws.getImageBucket())
                .key(objectKey(relativePath))
                .contentType(contentType)
                .contentLength((long) body.length)
                .build(), RequestBody.fromBytes(body));
    }

    public byte[] get(String relativePath) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(aws.getImageBucket())
                    .key(objectKey(relativePath))
                    .build()).asByteArray();
        } catch (NoSuchKeyException ex) {
            return null;
        }
    }

    private String objectKey(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains("..")) {
            throw new IllegalArgumentException("bad image path");
        }
        String prefix = aws.getImagePrefix() == null ? "" : aws.getImagePrefix();
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + relativePath;
    }
}
