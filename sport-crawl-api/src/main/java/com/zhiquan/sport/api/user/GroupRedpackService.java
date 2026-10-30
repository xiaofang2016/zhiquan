package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroupRedpackService {

    private final SportUserRepository users;
    private final JxtClient jxt;
    private final ObjectMapper json;

    public GroupRedpackService(SportUserRepository users, JxtClient jxt, ObjectMapper json) {
        this.users = users;
        this.jxt = jxt;
        this.json = json;
    }

    public Map<String, String> open(String raw) {
        if (raw == null || raw.length() > 2048) {
            throw new IllegalArgumentException("签名不正确");
        }
        JsonNode node;
        try {
            node = json.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("签名不正确");
        }
        String userId = node.path("userId").asText("");
        String phone = node.path("phone").asText("");
        String direction = node.path("direction").asText("");
        String centsText = node.path("cents").asText("");
        String tradeNo = node.path("tradeNo").asText("");
        String title = node.path("title").asText("");
        long now = Instant.now().getEpochSecond();
        try {
            GroupRedpack.check(jxt.outboundSecret(), node.path("timestamp").asText(""), node.path("nonce").asText(""),
                    userId, phone, direction, centsText, tradeNo, title, node.path("signature").asText(""), now);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("签名不正确");
        }
        SportUser user = users.find(phone).orElse(null);
        if (user == null || !userId.equals(user.getUserId())) {
            throw new IllegalArgumentException("请先登录");
        }
        long cents = Long.parseLong(centsText);
        Map<String, AttributeValue> row = ledger(phone, tradeNo, title, cents, "debit".equals(direction));
        try {
            if ("debit".equals(direction)) {
                users.debit(phone, cents, row);
            } else {
                users.credit(phone, cents, row);
            }
        } catch (TransactionCanceledException ex) {
            if (!already(ex)) {
                throw new IllegalArgumentException("debit".equals(direction) ? "余额不足" : "请先登录");
            }
        }
        SportUser latest = users.find(phone).orElse(user);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("balance", JxtMoney.yuan(latest.getBalanceCent()));
        return data;
    }

    private static boolean already(TransactionCanceledException ex) {
        List<CancellationReason> reasons = ex.cancellationReasons();
        return reasons != null && reasons.size() > 1 && reasons.get(1) != null
                && "ConditionalCheckFailed".equals(reasons.get(1).code());
    }

    private static Map<String, AttributeValue> ledger(String phone, String tradeNo, String title, long cents, boolean debit) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s(phone));
        item.put("sk", Attrs.s("tx#order#" + tradeNo));
        item.put("kind", Attrs.s("redpacket"));
        item.put("platform", Attrs.s(""));
        item.put("amount_cent", Attrs.n(cents));
        item.put("status", Attrs.n(1));
        item.put("title", Attrs.s(title));
        item.put("trade_no", Attrs.s(tradeNo));
        item.put("fail_reason", Attrs.s(""));
        item.put("direction", Attrs.s(debit ? "debit" : "credit"));
        item.put("created_at", Attrs.n(Instant.now().toEpochMilli()));
        return item;
    }
}
