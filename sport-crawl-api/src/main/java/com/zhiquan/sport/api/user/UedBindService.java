package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UedBindService {

    private final UserAuthService auth;
    private final SportUserRepository users;
    private final UedClient ued;
    private final WalletBook book;

    public UedBindService(UserAuthService auth, SportUserRepository users, UedClient ued, WalletBook book) {
        this.auth = auth;
        this.users = users;
        this.ued = ued;
        this.book = book;
    }

    public Map<String, String> status(String token) {
        return view(user(token));
    }

    public void sms(String token) {
        SportUser user = user(token);
        users.claimSmsSlot(user.getPhone(), "ued_sms_at");
        ued.sms(user.getPhone());
    }

    public Map<String, String> confirm(String token, String codeRaw) {
        SportUser user = user(token);
        String code = codeRaw == null ? "" : codeRaw.trim();
        if (!code.matches("\\d{4,6}")) {
            throw new IllegalArgumentException("请输入验证码");
        }
        remember(user, ued.confirm(user.getPhone(), code));
        return view(user);
    }

    public Map<String, String> password(String token, String usernameRaw, String passwordRaw) {
        SportUser user = user(token);
        String username = usernameRaw == null ? "" : usernameRaw.trim();
        String password = passwordRaw == null ? "" : passwordRaw;
        if (username.isEmpty() || username.length() > 64) {
            throw new IllegalArgumentException("请输入账号");
        }
        if (password.isBlank() || password.length() > 64) {
            throw new IllegalArgumentException("请输入密码");
        }
        remember(user, ued.login(username, password));
        return view(user);
    }

    private void remember(SportUser user, UedClient.UedAccount account) {
        String shown = account.username().isBlank() ? user.getPhone() : account.username();
        users.saveUed(user.getPhone(), account.uid(), shown, account.accessToken(), account.expireAt());
        user.setUedBound(true);
        user.setUedUid(account.uid());
        user.setUedAccount(shown);
        user.setUedAccessToken(account.accessToken());
        user.setUedTokenExpire(account.expireAt());
    }

    public Map<String, String> unbind(String token) {
        SportUser user = user(token);
        if (!user.isUedBound()) {
            throw new IllegalArgumentException("还没有绑定UED账号");
        }
        users.clearUed(user.getPhone());
        user.setUedBound(false);
        user.setUedUid(null);
        user.setUedAccount(null);
        user.setUedAccessToken(null);
        user.setUedTokenExpire(0);
        return view(user);
    }

    public Map<String, String> withdraw(String token, String amountRaw) {
        SportUser user = user(token);
        if (!user.isUedBound() || user.getUedUid() == null || user.getUedUid().isBlank()) {
            throw new IllegalArgumentException("请先绑定UED账号");
        }
        String access = user.getUedAccessToken();
        if (access == null || access.isBlank() || user.getUedTokenExpire() <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("请重新验证UED账号");
        }
        long cents = JxtMoney.cents(amountRaw);
        ued.requireTransfer();
        if (!users.deduct(user.getPhone(), cents)) {
            throw new IllegalArgumentException("余额不足");
        }
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        try {
            ued.transfer(user.getUedUid(), access, cents, orderNo);
        } catch (RuntimeException first) {
            try {
                ued.transfer(user.getUedUid(), access, cents, orderNo);
            } catch (RuntimeException second) {
                users.credit(user.getPhone(), cents);
                book.withdrawFailed(user, "ued", cents, orderNo, second.getMessage());
                throw refunded(second);
            }
        }
        user.setBalanceCent(Math.max(0, user.getBalanceCent() - cents));
        book.withdrawSuccess(user, "ued", cents, orderNo);
        Map<String, String> data = view(user);
        data.put("tradeNo", orderNo);
        return data;
    }

    private static IllegalArgumentException refunded(RuntimeException failure) {
        String reason = failure.getMessage();
        if (reason == null || reason.isBlank() || "UED接口失败".equals(reason)) {
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
        data.put("isBind", user.isUedBound() ? "1" : "0");
        data.put("account", JxtBindService.mask(user.getUedAccount()));
        data.put("balance", JxtMoney.yuan(user.getBalanceCent()));
        data.put("withdrawn", JxtMoney.yuan(user.getUedWithdrawnCent()));
        return data;
    }
}
