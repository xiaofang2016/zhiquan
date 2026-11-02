package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.user.JxtMoney;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.store.aws.Attrs;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ChatRedpackService {

    private final ZhiquanChatDebugService chat;
    private final UserAuthService auth;
    private final SportUserRepository users;

    public ChatRedpackService(ZhiquanChatDebugService chat, UserAuthService auth, SportUserRepository users) {
        this.chat = chat;
        this.auth = auth;
        this.users = users;
    }

    public Map<String, Object> claim(long groupId, String token, String messageId) {
        SportUser user = auth.account(token);
        AttributeValue content = chat.messageItem(groupId, messageId).get("content");
        String raw = content == null || content.s() == null ? "" : content.s();
        ChatRedpackRules.Packet packet = ChatRedpackRules.read(raw);
        if (!packet.open()) {
            throw new IllegalArgumentException("红包已领完");
        }
        String tradeNo = "chat-" + packet.walletId();
        Optional<Long> already = users.orderCent(user.getPhone(), tradeNo);
        if (already.isPresent()) {
            return paid(user, already.get());
        }
        int[] range = users.chatRedpackRange(ChatRedpackRules.DEFAULT_MIN_CENT, ChatRedpackRules.DEFAULT_MAX_CENT);
        long cent = ChatRedpackRules.draw(range[0], range[1], ThreadLocalRandom.current());
        try {
            users.credit(user.getPhone(), cent, ledger(user.getPhone(), tradeNo, cent));
        } catch (TransactionCanceledException ex) {
            Optional<Long> saved = users.orderCent(user.getPhone(), tradeNo);
            if (saved.isEmpty()) {
                throw new IllegalArgumentException("请稍后再试");
            }
            cent = saved.get();
        }
        SportUser latest = users.find(user.getPhone()).orElse(user);
        return paid(latest, cent);
    }

    public Map<String, Object> saveRange(int minCent, int maxCent) {
        ChatRedpackRules.checkRange(minCent, maxCent);
        users.saveChatRedpackRange(minCent, maxCent);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("minCent", minCent);
        data.put("maxCent", maxCent);
        return data;
    }

    private static Map<String, Object> paid(SportUser user, long cent) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", JxtMoney.yuan(cent));
        data.put("balance", JxtMoney.yuan(user.getBalanceCent()));
        data.put("status", "已领取");
        return data;
    }

    private static Map<String, AttributeValue> ledger(String phone, String tradeNo, long cents) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", Attrs.s(phone));
        item.put("sk", Attrs.s("tx#order#" + tradeNo));
        item.put("kind", Attrs.s("redpacket"));
        item.put("platform", Attrs.s(""));
        item.put("amount_cent", Attrs.n(cents));
        item.put("status", Attrs.n(1));
        item.put("title", Attrs.s("群红包"));
        item.put("trade_no", Attrs.s(tradeNo));
        item.put("fail_reason", Attrs.s(""));
        item.put("direction", Attrs.s(""));
        item.put("created_at", Attrs.n(Instant.now().toEpochMilli()));
        return item;
    }
}
