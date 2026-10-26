package com.zhiquan.sport.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MissionService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final ObjectMapper json;

    public MissionService(UserAuthService auth, SportUserRepository users, ObjectMapper json) {
        this.auth = auth;
        this.users = users;
        this.json = json;
    }

    public Map<String, Object> state(String token) {
        SportUser user = auth.account(token);
        return view(load(user.getPhone()).state(), LocalDate.now(MissionRules.ZONE));
    }

    public Map<String, Object> sign(String token, int day) {
        return change(token, current -> MissionRules.sign(current, day, LocalDateTime.now(MissionRules.ZONE)));
    }

    public Map<String, Object> claim(String token, int type) {
        return change(token, current -> MissionRules.claim(current, type, LocalDateTime.now(MissionRules.ZONE)));
    }

    private Map<String, Object> change(String token, java.util.function.UnaryOperator<MissionRules.State> action) {
        SportUser user = auth.account(token);
        for (int attempt = 0; attempt < 2; attempt++) {
            Loaded loaded = load(user.getPhone());
            MissionRules.State next = action.apply(loaded.state());
            try {
                users.saveMission(user.getPhone(), loaded.raw(), encode(next));
                return view(next, LocalDate.now(MissionRules.ZONE));
            } catch (ConditionalCheckFailedException ex) {
                if (attempt == 1) {
                    throw new IllegalArgumentException("请稍后再试");
                }
            }
        }
        throw new IllegalArgumentException("请稍后再试");
    }

    private Loaded load(String phone) {
        String raw = users.mission(phone);
        if (raw == null || raw.isBlank()) {
            return new Loaded("", MissionRules.normalize(MissionRules.fresh(), LocalDate.now(MissionRules.ZONE)));
        }
        MissionRules.State state;
        try {
            state = json.readValue(raw, MissionRules.State.class);
        } catch (Exception ex) {
            state = MissionRules.fresh();
        }
        return new Loaded(raw, MissionRules.normalize(state, LocalDate.now(MissionRules.ZONE)));
    }

    private String encode(MissionRules.State state) {
        try {
            return json.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalArgumentException("领取失败");
        }
    }

    private static Map<String, Object> view(MissionRules.State state, LocalDate today) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", state.balance());
        data.put("signDay", state.signDay());
        data.put("signed", today.toString().equals(state.signedOn()));
        data.put("gotReg", state.gotReg());
        data.put("gotLogin", state.gotLogin());
        data.put("records", state.records());
        return data;
    }

    private record Loaded(String raw, MissionRules.State state) {
    }
}
