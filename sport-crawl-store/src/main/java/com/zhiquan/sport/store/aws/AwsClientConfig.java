package com.zhiquan.sport.store.aws;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(SportAwsProperties.class)
public class AwsClientConfig {

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(SportAwsProperties properties) {
        return DynamoDbClient.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(SportAwsProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
