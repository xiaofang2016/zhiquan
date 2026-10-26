package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RedPackService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final ObjectMapper json;

    public RedPackService(UserAuthService auth, SportUserRepository users, ObjectMapper json) {
        this.auth = auth;
        this.users = users;
        this.json = json;
    }

    public Map<String, Object> state(String token) {
        SportUser user = blank(token) ? null : auth.account(token);
        return view(loadToday().round(), user);
    }

    public Map<String, Object> claim(String token) {
        SportUser user = auth.account(token);
        for (int attempt = 0; attempt < 2; attempt++) {
            Loaded loaded = loadToday();
            RedPackRules.Round next = RedPackRules.claim(loaded.round(), user.getUserId(),
                    LocalDateTime.now(MissionRules.ZONE), ThreadLocalRandom.current());
            long cent = next.claims().get(next.claims().size() - 1).cent();
            try {
                users.saveRedpack(loaded.day(), loaded.raw(), encode(next), user.getPhone(), cent);
                user.setBalanceCent(user.getBalanceCent() + cent);
                return view(next, user);
            } catch (TransactionCanceledException ex) {
                if (attempt == 1 || !conditional(ex)) {
                    throw new IllegalArgumentException("请稍后再试");
                }
            }
        }
        throw new IllegalArgumentException("请稍后再试");
    }

    private Loaded loadToday() {
        String day = LocalDate.now(MissionRules.ZONE).toString();
        String raw = users.redpack(day);
        if (raw == null || raw.isBlank()) {
            RedPackRules.Round fresh = RedPackRules.fresh();
            String encoded = encode(fresh);
            try {
                users.createRedpack(day, encoded);
                return new Loaded(day, encoded, fresh);
            } catch (ConditionalCheckFailedException ex) {
                raw = users.redpack(day);
            }
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("请稍后再试");
        }
        return new Loaded(day, raw, decode(raw));
    }

    private RedPackRules.Round decode(String raw) {
        try {
            RedPackRules.Round round = json.readValue(raw, RedPackRules.Round.class);
            if (round == null || round.count() <= 0) {
                throw new IllegalArgumentException("请稍后再试");
            }
            return round;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("请稍后再试");
        }
    }

    private String encode(RedPackRules.Round round) {
        try {
            return json.writeValueAsString(round);
        } catch (Exception ex) {
            throw new IllegalArgumentException("领取失败");
        }
    }

    private static boolean blank(String token) {
        return token == null || token.isBlank();
    }

    private static boolean conditional(TransactionCanceledException ex) {
        if (ex.cancellationReasons() == null) {
            return false;
        }
        for (CancellationReason reason : ex.cancellationReasons()) {
            if (reason != null && "ConditionalCheckFailed".equals(reason.code())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> view(RedPackRules.Round round, SportUser user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sender", "说球帝");
        data.put("description", "拼手气红包");
        data.put("num", round.count());
        data.put("drawNum", round.count() - round.remainCount());
        data.put("amount", JxtMoney.yuan(round.poolCent()));
        Long mine = user == null ? null : RedPackRules.mine(round, user.getUserId());
        data.put("mine", mine == null ? null : JxtMoney.yuan(mine));
        if (user != null) {
            data.put("balance", JxtMoney.yuan(user.getBalanceCent()));
        }
        return data;
    }

    private record Loaded(String day, String raw, RedPackRules.Round round) {
    }
}
