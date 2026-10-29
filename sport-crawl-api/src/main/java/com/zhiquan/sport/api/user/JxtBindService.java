package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JxtBindService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final JxtClient jxt;
    private final WalletBook book;

    public JxtBindService(UserAuthService auth, SportUserRepository users, JxtClient jxt, WalletBook book) {
        this.auth = auth;
        this.users = users;
        this.jxt = jxt;
        this.book = book;
    }

    public Map<String, String> status(String token) {
        return view(user(token));
    }

    public void sms(String token) {
        SportUser user = user(token);
        users.claimSmsSlot(user.getPhone(), "jxt_sms_at");
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
        users.saveJxt(user.getPhone(), account.uid(), shown, account.accessToken(), account.expireAt());
        user.setJxtBound(true);
        user.setJxtUid(account.uid());
        user.setJxtAccount(shown);
        user.setJxtAccessToken(account.accessToken());
        user.setJxtTokenExpire(account.expireAt());
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
        user.setJxtAccessToken(null);
        user.setJxtTokenExpire(0);
        return view(user);
    }

    public Map<String, String> withdraw(String token, String amountRaw) {
        SportUser user = user(token);
        if (!user.isJxtBound() || user.getJxtUid() == null || user.getJxtUid().isBlank()) {
            throw new IllegalArgumentException("请先绑定吉祥账号");
        }
        String access = user.getJxtAccessToken();
        if (access == null || access.isBlank() || user.getJxtTokenExpire() <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("请重新验证吉祥账号");
        }
        long cents = JxtMoney.cents(amountRaw);
        jxt.requireTransfer();
        if (!users.deduct(user.getPhone(), cents)) {
            throw new IllegalArgumentException("余额不足");
        }
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        try {
            jxt.transfer(user.getJxtUid(), access, cents, orderNo);
        } catch (RuntimeException first) {
            try {
                jxt.transfer(user.getJxtUid(), access, cents, orderNo);
            } catch (RuntimeException second) {
                users.credit(user.getPhone(), cents);
                book.withdrawFailed(user, "jxt", cents, orderNo, second.getMessage());
                throw refunded(second, "吉祥接口失败");
            }
        }
        user.setBalanceCent(Math.max(0, user.getBalanceCent() - cents));
        book.withdrawSuccess(user, "jxt", cents, orderNo);
        Map<String, String> data = view(user);
        data.put("tradeNo", orderNo);
        return data;
    }

    private static IllegalArgumentException refunded(RuntimeException failure, String generic) {
        String reason = failure.getMessage();
        if (reason == null || reason.isBlank() || generic.equals(reason)) {
            return new IllegalArgumentException("提现失败，余额已退回");
        }
        return new IllegalArgumentException("提现失败，余额已退回：" + reason);
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
        data.put("withdrawn", JxtMoney.yuan(user.getJxtWithdrawnCent()));
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
