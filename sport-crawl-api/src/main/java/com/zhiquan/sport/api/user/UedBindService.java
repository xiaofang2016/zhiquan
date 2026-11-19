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
        UedClient.UedAccount account = ued.confirm(user.getPhone(), code);
        String username = account.username().isBlank() ? user.getPhone() : account.username();
        UedClient.Inspect inspect = ued.inspectNewAccount(username, account.accessToken(), user.getPhone());
        if (inspect.replacement() != null) {
            account = inspect.replacement();
        }
        remember(user, account, inspect.need());
        return view(user);
    }

    public Map<String, String> setPassword(String token, String passwordRaw, String confirm) {
        SportUser user = user(token);
        if (!user.isUedBound() || user.getUedUid() == null || user.getUedUid().isBlank()) {
            throw new IllegalArgumentException("请先绑定UED账号");
        }
        if (!tokenFresh(user.getUedAccessToken(), user.getUedTokenExpire())) {
            throw new IllegalArgumentException("请重新验证UED账号");
        }
        if (!"1".equals(user.getUedNeedPassword())) {
            throw new IllegalArgumentException(VendorPassword.ALREADY);
        }
        VendorPassword.check(passwordRaw, confirm, user.getUedAccount(), user.getPhone(), true);
        try {
            ued.setLoginPassword(user.getUedAccessToken(), passwordRaw, user.getPhone());
        } catch (IllegalArgumentException ex) {
            if (VendorPassword.alreadyChanged(ex.getMessage())) {
                markPasswordSet(user);
                throw new IllegalArgumentException(VendorPassword.ALREADY);
            }
            throw ex;
        }
        markPasswordSet(user);
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
        remember(user, ued.login(username, password), "0");
        return view(user);
    }

    private void remember(SportUser user, UedClient.UedAccount account, String need) {
        String shown = account.username().isBlank() ? user.getPhone() : account.username();
        users.saveUed(user.getPhone(), account.uid(), shown, account.accessToken(), account.expireAt());
        user.setUedBound(true);
        user.setUedUid(account.uid());
        user.setUedAccount(shown);
        user.setUedAccessToken(account.accessToken());
        user.setUedTokenExpire(account.expireAt());
        if ("1".equals(need) || "0".equals(need)) {
            users.saveUedNeed(user.getPhone(), need);
            user.setUedNeedPassword(need);
        }
    }

    private void markPasswordSet(SportUser user) {
        users.saveUedNeed(user.getPhone(), "0");
        user.setUedNeedPassword("0");
    }

    private static boolean tokenFresh(String access, long expireAt) {
        return access != null && !access.isBlank() && expireAt > Instant.now().getEpochSecond();
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
        user.setUedNeedPassword(null);
        return view(user);
    }

    public Map<String, String> withdraw(String token, String amountRaw) {
        SportUser user = user(token);
        if (!user.isUedBound() || user.getUedUid() == null || user.getUedUid().isBlank()) {
            throw new IllegalArgumentException("请先绑定UED账号");
        }
        String access = user.getUedAccessToken();
        if (!tokenFresh(access, user.getUedTokenExpire())) {
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
        data.put("needPassword", "1".equals(user.getUedNeedPassword()) ? "1" : "0");
        return data;
    }
}
