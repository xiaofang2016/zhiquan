package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SmsCode;
import com.zhiquan.sport.store.entity.SportUser;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 账号在 {@code sport_user}。资料是 {@code sk=PROFILE}，明文密码是另一条 {@code sk=PASSWORD}。
 * 登录只读资料上的哈希。明文那条不参与校验，接口也不读它。
 * 验证码是 {@code sk=SMS}，用过或过期就删掉。
 */
@Repository
public class SportUserRepository {

    static final String PROFILE = "PROFILE";
    static final String PASSWORD = "PASSWORD";
    static final String SMS = "SMS";

    private final DynamoDbClient dynamo;
    private final SportAwsProperties aws;

    public SportUserRepository(DynamoDbClient dynamo, SportAwsProperties aws) {
        this.dynamo = dynamo;
        this.aws = aws;
    }

    public void ensureTable() {
        if (active()) {
            return;
        }
        try {
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName(aws.getUserTable())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build())
                    .build());
        } catch (ResourceInUseException ex) {
            // 另一台实例正在建同一张表。
        }
        for (int i = 0; i < 40 && !active(); i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean active() {
        try {
            return dynamo.describeTable(DescribeTableRequest.builder().tableName(aws.getUserTable()).build())
                    .table().tableStatus() == TableStatus.ACTIVE;
        } catch (ResourceNotFoundException ex) {
            return false;
        }
    }

    public Optional<SportUser> find(String phone) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        SportUser user = new SportUser();
        user.setPhone(Attrs.str(item, "phone"));
        user.setUserId(Attrs.str(item, "user_id"));
        user.setNickname(Attrs.str(item, "nickname"));
        user.setPasswordHash(Attrs.str(item, "password_hash"));
        user.setPasswordSalt(Attrs.str(item, "password_salt"));
        user.setCreatedAt(Attrs.str(item, "created_at"));
        user.setJxtBound("1".equals(Attrs.str(item, "jxt_bound")));
        user.setJxtUid(Attrs.str(item, "jxt_uid"));
        user.setJxtAccount(Attrs.str(item, "jxt_account"));
        Long balance = Attrs.lng(item, "balance_cent");
        user.setBalanceCent(balance == null ? 0 : balance);
        return Optional.of(user);
    }

    public void saveJxt(String phone, String uid, String account) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":bound", Attrs.s("1"));
        values.put(":uid", Attrs.s(uid));
        values.put(":account", Attrs.s(account));
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET jxt_bound = :bound, jxt_uid = :uid, jxt_account = :account")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(values)
                .build());
    }

    public void clearJxt(String phone) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET jxt_bound = :bound REMOVE jxt_uid, jxt_account")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(Map.of(":bound", Attrs.s("0")))
                .build());
    }

    public boolean deduct(String phone, long cents) {
        try {
            dynamo.updateItem(balanceUpdate(phone, cents, "-"));
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
    }

    public void credit(String phone, long cents) {
        dynamo.updateItem(balanceUpdate(phone, cents, "+"));
    }

    private UpdateItemRequest balanceUpdate(String phone, long cents, String op) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":zero", Attrs.n(0));
        values.put(":amt", Attrs.n(cents));
        String expression = "SET balance_cent = if_not_exists(balance_cent, :zero) " + op + " :amt";
        UpdateItemRequest.Builder builder = UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression(expression)
                .expressionAttributeValues(values);
        if ("-".equals(op)) {
            builder.conditionExpression("attribute_exists(pk) AND if_not_exists(balance_cent, :zero) >= :amt");
        } else {
            builder.conditionExpression("attribute_exists(pk)");
        }
        return builder.build();
    }

    public boolean insert(SportUser user, String passwordPlain) {
        Map<String, AttributeValue> profile = new HashMap<>();
        profile.put("pk", Attrs.s(user.getPhone()));
        profile.put("sk", Attrs.s(PROFILE));
        profile.put("phone", Attrs.s(user.getPhone()));
        profile.put("user_id", Attrs.s(user.getUserId()));
        profile.put("nickname", Attrs.s(user.getNickname()));
        profile.put("password_hash", Attrs.s(user.getPasswordHash()));
        profile.put("password_salt", Attrs.s(user.getPasswordSalt()));
        profile.put("created_at", Attrs.s(user.getCreatedAt()));
        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(aws.getUserTable())
                    .item(profile)
                    .conditionExpression("attribute_not_exists(pk)")
                    .build());
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
        Map<String, AttributeValue> plain = new HashMap<>();
        plain.put("pk", Attrs.s(user.getPhone()));
        plain.put("sk", Attrs.s(PASSWORD));
        plain.put("phone", Attrs.s(user.getPhone()));
        plain.put("password_plain", Attrs.s(passwordPlain));
        plain.put("updated_at", Attrs.s(user.getCreatedAt()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getUserTable()).item(plain).build());
        return true;
    }

    public Optional<SmsCode> findCode(String phone) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, SMS))
                .build()).item();
        if (item == null || item.isEmpty() || Attrs.str(item, "code") == null) {
            return Optional.empty();
        }
        Long sentAt = Attrs.lng(item, "sent_at");
        Long expireAt = Attrs.lng(item, "expire_at");
        return Optional.of(new SmsCode(Attrs.str(item, "code"), sentAt == null ? 0 : sentAt, expireAt == null ? 0 : expireAt));
    }

    public void saveCode(String phone, String code, long sentAt, long expireAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s(phone));
        item.put("sk", Attrs.s(SMS));
        item.put("phone", Attrs.s(phone));
        item.put("code", Attrs.s(code));
        item.put("sent_at", Attrs.n(sentAt));
        item.put("expire_at", Attrs.n(expireAt));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getUserTable()).item(item).build());
    }

    public void deleteCode(String phone) {
        dynamo.deleteItem(DeleteItemRequest.builder().tableName(aws.getUserTable()).key(key(phone, SMS)).build());
    }

    private static Map<String, AttributeValue> key(String phone, String sort) {
        return Map.of("pk", Attrs.s(phone), "sk", Attrs.s(sort));
    }
}
