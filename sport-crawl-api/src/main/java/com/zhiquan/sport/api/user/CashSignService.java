package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CashSignService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final WalletBook book;
    private final ObjectMapper json;

    public CashSignService(UserAuthService auth, SportUserRepository users, WalletBook book, ObjectMapper json) {
        this.auth = auth;
        this.users = users;
        this.book = book;
        this.json = json;
    }

    public Map<String, Object> status(String token) {
        SportUser user = auth.account(token);
        return view(user, read(user.getPhone()), null);
    }

    public Map<String, Object> claim(String token, Integer day) {
        SportUser user = auth.account(token);
        LocalDate today = LocalDate.now(MissionRules.ZONE);
        for (int attempt = 0; attempt < 2; attempt++) {
            String raw = users.profileJson(user.getPhone(), "cash_sign");
            CashSignRules.State next = CashSignRules.sign(decode(raw), day, today);
            int yuan = CashSignRules.yuan(next.signDay());
            try {
                book.sign(user, yuan * 100L, "sign-" + today.toString().replace("-", ""), raw, encode(next));
                user.setBalanceCent(user.getBalanceCent() + yuan * 100L);
                return view(user, next, yuan);
            } catch (TransactionCanceledException ex) {
                if (attempt == 1) {
                    throw new IllegalArgumentException("今日已签到");
                }
            }
        }
        throw new IllegalArgumentException("今日已签到");
    }

    private CashSignRules.State read(String phone) {
        return decode(users.profileJson(phone, "cash_sign"));
    }

    private CashSignRules.State decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return CashSignRules.fresh();
        }
        try {
            return json.readValue(raw, CashSignRules.State.class);
        } catch (Exception ex) {
            return CashSignRules.fresh();
        }
    }

    private String encode(CashSignRules.State state) {
        try {
            return json.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalArgumentException("签到失败");
        }
    }

    private static Map<String, Object> view(SportUser user, CashSignRules.State state, Integer claimedYuan) {
        LocalDate today = LocalDate.now(MissionRules.ZONE);
        CashSignRules.State shown = CashSignRules.normalize(state, today);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("signDay", shown.signDay());
        data.put("signed", !CashSignRules.canSign(state, today));
        data.put("amounts", List.of(1, 2, 3, 5, 6, 8, 10));
        data.put("today", CashSignRules.shownYuan(state, today));
        data.put("canSign", CashSignRules.canSign(state, today));
        data.put("balance", JxtMoney.yuan(user.getBalanceCent()));
        if (claimedYuan != null) {
            data.put("amount", JxtMoney.yuan(claimedYuan * 100L));
        }
        return data;
    }
}
