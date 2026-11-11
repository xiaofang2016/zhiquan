package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 说球帝余额流水。提现成功累加累计提现；失败退回只记状态，不计入累计。
 */
@Service
public class WalletBook {

    static final int PAGE_SIZE = 10;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(SHANGHAI);

    private final SportUserRepository users;

    public WalletBook(SportUserRepository users) {
        this.users = users;
    }

    public void withdrawSuccess(SportUser user, String platform, long cents, String orderNo) {
        String field = "jxt".equals(platform) ? "jxt_withdrawn_cent" : "ued_withdrawn_cent";
        long now = Instant.now().toEpochMilli();
        users.noteWithdraw(user.getPhone(), field, cents, row(user.getPhone(), now, orderNo, "withdraw", platform, cents, 1, title(platform), ""));
        if ("jxt".equals(platform)) {
            user.setJxtWithdrawnCent(user.getJxtWithdrawnCent() + cents);
        } else {
            user.setUedWithdrawnCent(user.getUedWithdrawnCent() + cents);
        }
    }

    public void withdrawFailed(SportUser user, String platform, long cents, String orderNo, String reason) {
        long now = Instant.now().toEpochMilli();
        put(row(user.getPhone(), now, orderNo, "withdraw", platform, cents, 2, "提现失败，余额已退回", reason == null ? "" : reason));
    }

    public void sign(SportUser user, long cents, String orderNo, String expectedSign, String nextSign) {
        long now = Instant.now().toEpochMilli();
        users.claimCashSign(user.getPhone(), expectedSign, nextSign, cents,
                row(user.getPhone(), now, orderNo, "bill", "", cents, 1, "签到", ""));
    }

    public void redpack(SportUser user, long cents, String orderNo) {
        long now = Instant.now().toEpochMilli();
        put(row(user.getPhone(), now, orderNo, "redpacket", "", cents, 1, "红包", ""));
    }

    /** @return 这次新入账。同一单号已经记过时为 false */
    public boolean postRedpack(SportUser user, long cents, String orderNo) {
        long now = Instant.now().toEpochMilli();
        try {
            users.credit(user.getPhone(), cents, row(user.getPhone(), now, orderNo, "redpacket", "", cents, 1, "帖子红包", ""));
            return true;
        } catch (TransactionCanceledException ex) {
            return false;
        }
    }

    /** 打赏从付款人扣到收款人。作者是原站用户时不要调用。 */
    public void reward(SportUser from, SportUser to, long cents, String orderNo) {
        if (!users.deduct(from.getPhone(), cents)) {
            throw new IllegalArgumentException("余额不足");
        }
        long now = Instant.now().toEpochMilli();
        try {
            users.credit(to.getPhone(), cents, row(to.getPhone(), now, orderNo + "-in", "bill", "", cents, 1, "帖子打赏", ""));
        } catch (RuntimeException ex) {
            users.credit(from.getPhone(), cents);
            throw new IllegalArgumentException("打赏失败，余额已退回");
        }
        put(row(from.getPhone(), now, orderNo, "bill", "", cents, 1, "帖子打赏", "", "debit"));
    }

    public Map<String, Object> page(SportUser user, String kind, Integer pageRaw) {
        String normalized = kind == null ? "bill" : kind.trim();
        if (!"bill".equals(normalized) && !"redpacket".equals(normalized) && !"withdraw".equals(normalized)) {
            throw new IllegalArgumentException("流水类型无效");
        }
        int page = pageRaw == null || pageRaw < 1 ? 1 : pageRaw;
        List<Map<String, AttributeValue>> matched = new ArrayList<>();
        for (Map<String, AttributeValue> item : users.listLedger(user.getPhone())) {
            String itemKind = Attrs.str(item, "kind");
            if (!"bill".equals(normalized) && !normalized.equals(itemKind)) {
                continue;
            }
            matched.add(item);
        }
        int from = (page - 1) * PAGE_SIZE;
        List<Map<String, Object>> slice = new ArrayList<>();
        if (from < matched.size()) {
            for (Map<String, AttributeValue> item : matched.subList(from, Math.min(matched.size(), from + PAGE_SIZE))) {
                slice.add(view(item));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page);
        data.put("size", PAGE_SIZE);
        data.put("total", matched.size());
        data.put("records", slice);
        if ("redpacket".equals(normalized)) {
            putRedpacketTotals(data, matched);
        }
        return data;
    }

    static void putRedpacketTotals(Map<String, Object> data, List<Map<String, AttributeValue>> rows) {
        long sent = 0;
        long received = 0;
        int sentCount = 0;
        int receivedCount = 0;
        for (Map<String, AttributeValue> item : rows) {
            Integer status = Attrs.num(item, "status");
            if (status != null && status != 1) {
                continue;
            }
            long cents = Attrs.lng(item, "amount_cent") == null ? 0 : Attrs.lng(item, "amount_cent");
            if ("debit".equals(Attrs.str(item, "direction"))) {
                sent += Math.abs(cents);
                sentCount++;
            } else {
                received += Math.abs(cents);
                receivedCount++;
            }
        }
        data.put("sentCount", sentCount);
        data.put("sentAmount", JxtMoney.yuan(sent));
        data.put("receivedCount", receivedCount);
        data.put("receivedAmount", JxtMoney.yuan(received));
    }

    private void put(Map<String, AttributeValue> row) {
        try {
            users.putLedger(row);
        } catch (ConditionalCheckFailedException ignored) {
            // 同一单号已经记过
        }
    }

    private static Map<String, AttributeValue> row(String phone, long now, String orderNo, String kind, String platform, long cents, int status, String title, String reason) {
        return row(phone, now, orderNo, kind, platform, cents, status, title, reason, "");
    }

    private static Map<String, AttributeValue> row(String phone, long now, String orderNo, String kind, String platform, long cents, int status, String title, String reason, String direction) {
        String id = orderNo == null || orderNo.isBlank() ? Long.toString(now) : orderNo;
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s(phone));
        item.put("sk", Attrs.s(sortKey(now, id)));
        item.put("kind", Attrs.s(kind));
        item.put("platform", Attrs.s(platform));
        item.put("amount_cent", Attrs.n(cents));
        item.put("status", Attrs.n(status));
        item.put("title", Attrs.s(title));
        item.put("trade_no", Attrs.s(id));
        item.put("fail_reason", Attrs.s(reason));
        item.put("direction", Attrs.s(direction));
        item.put("created_at", Attrs.n(now));
        return item;
    }

    static String sortKey(long epochMillis, String id) {
        long inverted = Long.MAX_VALUE - Math.max(epochMillis, 0);
        return "tx#" + String.format("%020d", inverted) + "#" + id;
    }

    private static Map<String, Object> view(Map<String, AttributeValue> item) {
        String kind = Attrs.str(item, "kind");
        long cents = Attrs.lng(item, "amount_cent") == null ? 0 : Attrs.lng(item, "amount_cent");
        int status = Attrs.num(item, "status") == null ? 1 : Attrs.num(item, "status");
        long created = Attrs.lng(item, "created_at") == null ? 0 : Attrs.lng(item, "created_at");
        boolean outgoing = "withdraw".equals(kind) || "debit".equals(Attrs.str(item, "direction"));
        long signed = outgoing ? -Math.abs(cents) : Math.abs(cents);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tradeNo", Attrs.str(item, "trade_no"));
        row.put("kind", kind);
        row.put("platform", Attrs.str(item, "platform"));
        row.put("title", Attrs.str(item, "title"));
        row.put("amount", JxtMoney.yuan(signed));
        row.put("status", status);
        row.put("statusName", statusName(status));
        row.put("failReason", Attrs.str(item, "fail_reason") == null ? "" : Attrs.str(item, "fail_reason"));
        row.put("time", created <= 0 ? "" : STAMP.format(Instant.ofEpochMilli(created)));
        row.put("createdAt", created);
        return row;
    }

    private static String title(String platform) {
        return "ued".equals(platform) ? "提现到UED" : "提现到吉祥";
    }

    private static String statusName(int status) {
        if (status == 2) {
            return "失败已退回";
        }
        if (status == 0) {
            return "处理中";
        }
        return "成功";
    }
}
