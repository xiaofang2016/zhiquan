package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JxtBindService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final JxtClient jxt;

    public JxtBindService(UserAuthService auth, SportUserRepository users, JxtClient jxt) {
        this.auth = auth;
        this.users = users;
        this.jxt = jxt;
    }

    public Map<String, String> status(String token) {
        return view(user(token));
    }

    public void sms(String token) {
        SportUser user = user(token);
        jxt.sms(user.getPhone());
    }

    public Map<String, String> confirm(String token, String codeRaw) {
        SportUser user = user(token);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (!code.matches("\\d{4,6}")) {
            throw new IllegalArgumentException("请输入验证码");
        }
        JxtClient.JxtAccount account = jxt.confirm(user.getPhone(), code);
        String shown = account.username().isBlank() ? user.getPhone() : account.username();
        users.saveJxt(user.getPhone(), account.uid(), shown);
        user.setJxtBound(true);
        user.setJxtUid(account.uid());
        user.setJxtAccount(shown);
        return view(user);
    }

    public Map<String, String> unbind(String token) {
        SportUser user = user(token);
        if (!user.isJxtBound()) {
            throw new IllegalArgumentException("还没有绑定吉祥账号");
        }
        users.clearJxt(user.getPhone());
        user.setJxtBound(false);
        user.setJxtUid(null);
        user.setJxtAccount(null);
        return view(user);
    }

    public Map<String, String> withdraw(String token, String amountRaw) {
        SportUser user = user(token);
        if (!user.isJxtBound() || user.getJxtUid() == null || user.getJxtUid().isBlank()) {
            throw new IllegalArgumentException("请先绑定吉祥账号");
        }
        JxtMoney.cents(amountRaw);
        throw new IllegalArgumentException("转入吉祥还没接上");
    }

    private SportUser user(String token) {
        Map<String, String> session = auth.session(token);
        return users.find(session.get("phone")).orElseThrow(() -> new IllegalArgumentException("请先登录"));
    }

    private static Map<String, String> view(SportUser user) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("isBind", user.isJxtBound() ? "1" : "0");
        data.put("account", mask(user.getJxtAccount()));
        data.put("balance", JxtMoney.yuan(user.getBalanceCent()));
        return data;
    }

    static String mask(String account) {
        if (account == null || account.isBlank()) {
            return "";
        }
        if (account.length() <= 2) {
            return account;
        }
        return account.charAt(0) + "***" + account.charAt(account.length() - 1);
    }
}
