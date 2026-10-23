package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.SportAwsProperties;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Repository
public class S3DatabaseStore {

    private final S3Client s3;
    private final SportAwsProperties aws;

    public S3DatabaseStore(S3Client s3, SportAwsProperties aws) {
        this.s3 = s3;
        this.aws = aws;
    }

    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(aws.getImageBucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (NoSuchKeyException ex) {
            return null;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return null;
            }
            throw ex;
        }
    }

    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(aws.getImageBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    public void put(String key, byte[] body) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(aws.getImageBucket())
                .key(key)
                .contentType("application/json")
                .contentLength((long) body.length)
                .build(), RequestBody.fromBytes(body));
    }
}
