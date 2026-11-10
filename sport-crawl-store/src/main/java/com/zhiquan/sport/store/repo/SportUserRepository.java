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
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    static final String REDPACK = "REDPACK";

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
        user.setAvatar(Attrs.str(item, "avatar"));
        user.setCityName(Attrs.str(item, "city_name"));
        user.setPasswordHash(Attrs.str(item, "password_hash"));
        user.setPasswordSalt(Attrs.str(item, "password_salt"));
        user.setCreatedAt(Attrs.str(item, "created_at"));
        user.setJxtBound("1".equals(Attrs.str(item, "jxt_bound")));
        user.setJxtUid(Attrs.str(item, "jxt_uid"));
        user.setJxtAccount(Attrs.str(item, "jxt_account"));
        user.setJxtAccessToken(Attrs.str(item, "jxt_token"));
        Long tokenExpire = Attrs.lng(item, "jxt_token_expire");
        user.setJxtTokenExpire(tokenExpire == null ? 0 : tokenExpire);
        user.setUedBound("1".equals(Attrs.str(item, "ued_bound")));
        user.setUedUid(Attrs.str(item, "ued_uid"));
        user.setUedAccount(Attrs.str(item, "ued_account"));
        user.setUedAccessToken(Attrs.str(item, "ued_token"));
        Long uedExpire = Attrs.lng(item, "ued_token_expire");
        user.setUedTokenExpire(uedExpire == null ? 0 : uedExpire);
        Long balance = Attrs.lng(item, "balance_cent");
        user.setBalanceCent(balance == null ? 0 : balance);
        Long jxtWithdrawn = Attrs.lng(item, "jxt_withdrawn_cent");
        user.setJxtWithdrawnCent(jxtWithdrawn == null ? 0 : jxtWithdrawn);
        Long uedWithdrawn = Attrs.lng(item, "ued_withdrawn_cent");
        user.setUedWithdrawnCent(uedWithdrawn == null ? 0 : uedWithdrawn);
        return Optional.of(user);
    }

    public String mission(String phone) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .projectionExpression("mission")
                .build()).item();
        if (item == null || item.isEmpty()) {
            return "";
        }
        String saved = Attrs.str(item, "mission");
        return saved == null ? "" : saved;
    }

    public void saveMission(String phone, String expected, String next) {
        saveProfileJson(phone, "mission", expected, next);
    }

    public void saveSearch(String phone, String expected, String next) {
        saveProfileJson(phone, "search", expected, next);
    }

    private void saveProfileJson(String phone, String attribute, String expected, String next) {
        checkProfileField(attribute);
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":next", Attrs.s(next));
        String condition = "attribute_exists(pk)";
        if (expected == null || expected.isEmpty()) {
            condition += " AND attribute_not_exists(#attr)";
        } else {
            condition += " AND #attr = :expected";
            values.put(":expected", Attrs.s(expected));
        }
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET #attr = :next")
                .conditionExpression(condition)
                .expressionAttributeNames(Map.of("#attr", attribute))
                .expressionAttributeValues(values)
                .build());
    }

    public void saveCity(String phone, String cityName) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET city_name = :city")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(Map.of(":city", Attrs.s(cityName == null ? "" : cityName)))
                .build());
    }

    public void saveAvatar(String phone, String avatar) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET avatar = :avatar")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(Map.of(":avatar", Attrs.s(avatar)))
                .build());
    }

    public void saveJxt(String phone, String uid, String account, String token, long tokenExpire) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":bound", Attrs.s("1"));
        values.put(":uid", Attrs.s(uid));
        values.put(":account", Attrs.s(account));
        values.put(":token", Attrs.s(token));
        values.put(":expire", Attrs.n(tokenExpire));
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET jxt_bound = :bound, jxt_uid = :uid, jxt_account = :account, jxt_token = :token, jxt_token_expire = :expire")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(values)
                .build());
    }

    public void saveUed(String phone, String uid, String account, String token, long tokenExpire) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":bound", Attrs.s("1"));
        values.put(":uid", Attrs.s(uid));
        values.put(":account", Attrs.s(account));
        values.put(":token", Attrs.s(token));
        values.put(":expire", Attrs.n(tokenExpire));
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET ued_bound = :bound, ued_uid = :uid, ued_account = :account, ued_token = :token, ued_token_expire = :expire")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(values)
                .build());
    }

    public void clearUed(String phone) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET ued_bound = :bound REMOVE ued_uid, ued_account, ued_token, ued_token_expire")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(Map.of(":bound", Attrs.s("0")))
                .build());
    }

    public void clearJxt(String phone) {
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET jxt_bound = :bound REMOVE jxt_uid, jxt_account, jxt_token, jxt_token_expire")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(Map.of(":bound", Attrs.s("0")))
                .build());
    }

    /** 同一单号只扣一次。流水已存在时整笔取消。余额不够时也取消。 */
    public void debit(String phone, long cents, Map<String, AttributeValue> ledger) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":amt", Attrs.n(cents));
        Update balance = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET balance_cent = balance_cent - :amt")
                .conditionExpression("attribute_exists(pk) AND balance_cent >= :amt")
                .expressionAttributeValues(values)
                .build();
        Put row = Put.builder()
                .tableName(aws.getUserTable())
                .item(ledger)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(balance).build(),
                        TransactWriteItem.builder().put(row).build())
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

    public void updatePassword(String phone, String hash, String salt, String plain) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":hash", Attrs.s(hash));
        values.put(":salt", Attrs.s(salt));
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET password_hash = :hash, password_salt = :salt")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(values)
                .build());
        Map<String, AttributeValue> stored = new HashMap<>();
        stored.put("pk", Attrs.s(phone));
        stored.put("sk", Attrs.s(PASSWORD));
        stored.put("phone", Attrs.s(phone));
        stored.put("password_plain", Attrs.s(plain));
        stored.put("updated_at", Attrs.s(java.time.Instant.now().toString()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getUserTable()).item(stored).build());
    }

    /**
     * 记一笔成功提现，并累加该平台的累计提现。
     *
     * @param withdrawnField 只允许 jxt_withdrawn_cent 或 ued_withdrawn_cent
     */
    public void noteWithdraw(String phone, String withdrawnField, long cents, Map<String, AttributeValue> ledger) {
        if (!"jxt_withdrawn_cent".equals(withdrawnField) && !"ued_withdrawn_cent".equals(withdrawnField)) {
            throw new IllegalStateException("unknown withdrawn field");
        }
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":zero", Attrs.n(0));
        values.put(":amt", Attrs.n(cents));
        Update withdrawn = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET #w = if_not_exists(#w, :zero) + :amt")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeNames(Map.of("#w", withdrawnField))
                .expressionAttributeValues(values)
                .build();
        Put row = Put.builder()
                .tableName(aws.getUserTable())
                .item(ledger)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(withdrawn).build(),
                        TransactWriteItem.builder().put(row).build())
                .build());
    }

    public void putLedger(Map<String, AttributeValue> ledger) {
        dynamo.putItem(PutItemRequest.builder()
                .tableName(aws.getUserTable())
                .item(ledger)
                .conditionExpression("attribute_not_exists(pk)")
                .build());
    }

    public List<Map<String, AttributeValue>> listLedger(String phone) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":pk", Attrs.s(phone));
        values.put(":tx", Attrs.s("tx#"));
        return new ArrayList<>(dynamo.query(QueryRequest.builder()
                .tableName(aws.getUserTable())
                .keyConditionExpression("pk = :pk AND begins_with(sk, :tx)")
                .expressionAttributeValues(values)
                .scanIndexForward(true)
                .limit(1000)
                .build()).items());
    }

    public String redpack(String day) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(REDPACK, day))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return "";
        }
        String saved = Attrs.str(item, "pack");
        return saved == null ? "" : saved;
    }

    public void createRedpack(String day, String json) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s(REDPACK));
        item.put("sk", Attrs.s(day));
        item.put("pack", Attrs.s(json));
        dynamo.putItem(PutItemRequest.builder()
                .tableName(aws.getUserTable())
                .item(item)
                .conditionExpression("attribute_not_exists(pk)")
                .build());
    }

    public void saveRedpack(String day, String expected, String next, String phone, long cents) {
        Map<String, AttributeValue> packValues = new HashMap<>();
        packValues.put(":next", Attrs.s(next));
        packValues.put(":expected", Attrs.s(expected));
        Update pack = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(REDPACK, day))
                .updateExpression("SET pack = :next")
                .conditionExpression("attribute_exists(pk) AND pack = :expected")
                .expressionAttributeValues(packValues)
                .build();
        Map<String, AttributeValue> balanceValues = new HashMap<>();
        balanceValues.put(":zero", Attrs.n(0));
        balanceValues.put(":amt", Attrs.n(cents));
        Update balance = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET balance_cent = if_not_exists(balance_cent, :zero) + :amt")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(balanceValues)
                .build();
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(pack).build(),
                        TransactWriteItem.builder().update(balance).build())
                .build());
    }

    /** 同一单号只入账一次。流水已存在时整笔取消，余额不会再加。 */
    public void credit(String phone, long cents, Map<String, AttributeValue> ledger) {
        Map<String, AttributeValue> balanceValues = new HashMap<>();
        balanceValues.put(":zero", Attrs.n(0));
        balanceValues.put(":amt", Attrs.n(cents));
        Update balance = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET balance_cent = if_not_exists(balance_cent, :zero) + :amt")
                .conditionExpression("attribute_exists(pk)")
                .expressionAttributeValues(balanceValues)
                .build();
        Put row = Put.builder()
                .tableName(aws.getUserTable())
                .item(ledger)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(balance).build(),
                        TransactWriteItem.builder().put(row).build())
                .build());
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
            builder.conditionExpression("attribute_exists(pk) AND balance_cent >= :amt");
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

    /**
     * 同一手机号 60 秒内只发一次绑定验证码。没到时间抛出业务错误。
     *
     * @param phone 手机号
     * @param field 记录上次发送时间的字段，只允许 jxt_sms_at 或 ued_sms_at
     */
    public void claimSmsSlot(String phone, String field) {
        if (!"jxt_sms_at".equals(field) && !"ued_sms_at".equals(field)) {
            throw new IllegalStateException("unknown sms slot");
        }
        long now = java.time.Instant.now().getEpochSecond();
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":now", Attrs.n(now));
        values.put(":limit", Attrs.n(now - 60));
        try {
            dynamo.updateItem(UpdateItemRequest.builder()
                    .tableName(aws.getUserTable())
                    .key(key(phone, PROFILE))
                    .updateExpression("SET #at = :now")
                    .conditionExpression("attribute_exists(pk) AND (attribute_not_exists(#at) OR #at <= :limit)")
                    .expressionAttributeNames(Map.of("#at", field))
                    .expressionAttributeValues(values)
                    .build());
        } catch (ConditionalCheckFailedException ex) {
            throw new IllegalArgumentException("当前操作太频繁请您1分钟后再尝试");
        }
    }

    /** 群红包领取金额区间，单位分。没有配置时用调用方给的默认值。 */
    public int[] chatRedpackRange(int defaultMin, int defaultMax) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key("chat-redpack", "config"))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return new int[] {defaultMin, defaultMax};
        }
        Long min = Attrs.lng(item, "min_cent");
        Long max = Attrs.lng(item, "max_cent");
        if (min == null || max == null) {
            return new int[] {defaultMin, defaultMax};
        }
        return new int[] {min.intValue(), max.intValue()};
    }

    public void saveChatRedpackRange(int minCent, int maxCent) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s("chat-redpack"));
        item.put("sk", Attrs.s("config"));
        item.put("min_cent", Attrs.n(minCent));
        item.put("max_cent", Attrs.n(maxCent));
        item.put("updated_at", Attrs.n(System.currentTimeMillis()));
        dynamo.putItem(PutItemRequest.builder().tableName(aws.getUserTable()).item(item).build());
    }

    public String profileJson(String phone, String attribute) {
        checkProfileField(attribute);
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .projectionExpression("#attr")
                .expressionAttributeNames(Map.of("#attr", attribute))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return "";
        }
        String saved = Attrs.str(item, attribute);
        return saved == null ? "" : saved;
    }

    public void saveMissionAndPredict(String phone, String missionExpected, String missionNext, String predictExpected, String predictNext) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":mission", Attrs.s(missionNext));
        values.put(":predict", Attrs.s(predictNext));
        String condition = "attribute_exists(pk)";
        condition = expect(condition, values, "mission", missionExpected, ":missionWas");
        condition = expect(condition, values, "predict", predictExpected, ":predictWas");
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET mission = :mission, predict = :predict")
                .conditionExpression(condition)
                .expressionAttributeValues(values)
                .build());
    }

    /** 签到状态、余额、账单同一笔写入。任一条对不上就整笔取消。 */
    public void claimCashSign(String phone, String expected, String next, long cents, Map<String, AttributeValue> ledger) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":next", Attrs.s(next));
        values.put(":zero", Attrs.n(0));
        values.put(":amt", Attrs.n(cents));
        String condition = expect("attribute_exists(pk)", values, "cash_sign", expected, ":expected");
        Update profile = Update.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, PROFILE))
                .updateExpression("SET cash_sign = :next, balance_cent = if_not_exists(balance_cent, :zero) + :amt")
                .conditionExpression(condition)
                .expressionAttributeValues(values)
                .build();
        Put row = Put.builder()
                .tableName(aws.getUserTable())
                .item(ledger)
                .conditionExpression("attribute_not_exists(pk)")
                .build();
        dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(
                        TransactWriteItem.builder().update(profile).build(),
                        TransactWriteItem.builder().put(row).build())
                .build());
    }

    /**
     * 把这个号码下的资料、密码和流水改到新号码。验证码留在原处并删掉。
     * 新号码已经是别的用户时拒绝。写到一半再调用会接着搬完。
     */
    public void relocate(String from, String to) {
        List<Map<String, AttributeValue>> rows = queryAll(from);
        Map<String, AttributeValue> profile = null;
        for (Map<String, AttributeValue> row : rows) {
            if (PROFILE.equals(Attrs.str(row, "sk"))) {
                profile = row;
            }
        }
        if (profile == null) {
            throw new IllegalArgumentException("请先登录");
        }
        String userId = Attrs.str(profile, "user_id");
        Optional<SportUser> occupied = find(to);
        if (occupied.isPresent() && (userId == null || !userId.equals(occupied.get().getUserId()))) {
            throw new IllegalArgumentException("这个手机号已经注册");
        }
        for (Map<String, AttributeValue> row : rows) {
            String sort = Attrs.str(row, "sk");
            if (sort == null || PhoneMove.skip(sort)) {
                continue;
            }
            PutItemRequest.Builder put = PutItemRequest.builder()
                    .tableName(aws.getUserTable())
                    .item(PhoneMove.copy(row, to));
            if (PROFILE.equals(sort)) {
                put.conditionExpression("attribute_not_exists(pk) OR user_id = :uid")
                        .expressionAttributeValues(Map.of(":uid", Attrs.s(userId == null ? "" : userId)));
            }
            try {
                dynamo.putItem(put.build());
            } catch (ConditionalCheckFailedException ex) {
                throw new IllegalArgumentException("这个手机号已经注册");
            }
        }
        for (Map<String, AttributeValue> row : rows) {
            String sort = Attrs.str(row, "sk");
            if (sort == null) {
                continue;
            }
            dynamo.deleteItem(DeleteItemRequest.builder().tableName(aws.getUserTable()).key(key(from, sort)).build());
        }
    }

    private List<Map<String, AttributeValue>> queryAll(String phone) {
        List<Map<String, AttributeValue>> rows = new ArrayList<>();
        Map<String, AttributeValue> start = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(aws.getUserTable())
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(":pk", Attrs.s(phone)));
            if (start != null && !start.isEmpty()) {
                builder.exclusiveStartKey(start);
            }
            var response = dynamo.query(builder.build());
            if (response.items() != null) {
                rows.addAll(response.items());
            }
            start = response.lastEvaluatedKey();
        } while (start != null && !start.isEmpty());
        return rows;
    }

    private static String expect(String condition, Map<String, AttributeValue> values, String attribute, String expected, String token) {
        if (expected == null || expected.isEmpty()) {
            return condition + " AND attribute_not_exists(" + attribute + ")";
        }
        values.put(token, Attrs.s(expected));
        return condition + " AND " + attribute + " = " + token;
    }

    private static void checkProfileField(String attribute) {
        if (!"mission".equals(attribute) && !"predict".equals(attribute) && !"cash_sign".equals(attribute)
                && !"search".equals(attribute)) {
            throw new IllegalStateException("unknown profile field");
        }
    }

    /** 这个单号已经入过账时返回当时的分。 */
    public Optional<Long> orderCent(String phone, String tradeNo) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(aws.getUserTable())
                .key(key(phone, "tx#order#" + tradeNo))
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        Long cents = Attrs.lng(item, "amount_cent");
        return cents == null ? Optional.empty() : Optional.of(cents);
    }

    private static Map<String, AttributeValue> key(String phone, String sort) {
        return Map.of("pk", Attrs.s(phone), "sk", Attrs.s(sort));
    }
}
