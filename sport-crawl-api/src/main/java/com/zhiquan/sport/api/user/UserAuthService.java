package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.entity.SmsCode;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SportUserRepository users;
    private final S3ImageStore images;
    private final NexsmsClient sms;
    private final String secret;
    private final int days;
    private final int resendSeconds;
    private final int expireSeconds;

    public UserAuthService(SportUserRepository users,
                           S3ImageStore images,
                           NexsmsClient sms,
                           @Value("${sport.auth.token-secret}") String secret,
                           @Value("${sport.auth.token-days:30}") int days,
                           @Value("${sport.sms.resend-seconds:60}") int resendSeconds,
                           @Value("${sport.sms.expire-seconds:300}") int expireSeconds) {
        this.users = users;
        this.images = images;
        this.sms = sms;
        this.secret = secret;
        this.days = days;
        this.resendSeconds = resendSeconds;
        this.expireSeconds = expireSeconds;
    }

    public void sendCode(String phoneRaw) {
        sendSms(phoneRaw, false);
    }

    public void sendResetCode(String phoneRaw) {
        sendSms(phoneRaw, true);
    }

    public void sendLoginCode(String phoneRaw) {
        sendSms(phoneRaw, true);
    }

    public void resetPassword(String phoneRaw, String codeRaw, String passwordRaw, String confirm) {
        String phone = AccountCheck.phone(phoneRaw);
        String password = AccountCheck.password(passwordRaw);
        AccountCheck.confirm(password, confirm);
        acceptCode(phone, AccountCheck.code(codeRaw));
        if (users.find(phone).isEmpty()) {
            throw new IllegalArgumentException("这个手机号还没有注册");
        }
        String salt = UserPassword.salt();
        users.updatePassword(phone, UserPassword.hash(password, salt), salt, password);
    }

    public void changePassword(String token, String passwordRaw, String confirm) {
        SportUser user = account(token);
        String password = AccountCheck.password(passwordRaw);
        AccountCheck.confirm(password, confirm);
        String salt = UserPassword.salt();
        users.updatePassword(user.getPhone(), UserPassword.hash(password, salt), salt, password);
    }

    public Map<String, String> loginByCode(String phoneRaw, String codeRaw) {
        String phone = AccountCheck.phone(phoneRaw);
        acceptCode(phone, AccountCheck.code(codeRaw));
        SportUser user = users.find(phone).orElseThrow(() -> new IllegalArgumentException("这个手机号还没有注册"));
        return view(user, issue(user));
    }

    private void sendSms(String phoneRaw, boolean mustExist) {
        String phone = AccountCheck.phone(phoneRaw);
        boolean exists = users.find(phone).isPresent();
        if (mustExist && !exists) {
            throw new IllegalArgumentException("这个手机号还没有注册");
        }
        if (!mustExist && exists) {
            throw new IllegalArgumentException("这个手机号已经注册");
        }
        long now = Instant.now().getEpochSecond();
        users.findCode(phone).ifPresent(saved -> {
            if (now - saved.sentAt() < resendSeconds) {
                throw new IllegalArgumentException("请稍后再获取验证码");
            }
        });
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        users.saveCode(phone, code, now, now + expireSeconds);
        try {
            sms.send(phone, code);
        } catch (RuntimeException ex) {
            users.deleteCode(phone);
            throw ex;
        }
    }

    public Map<String, String> register(String phoneRaw, String passwordRaw, String confirm, String codeRaw) {
        String phone = AccountCheck.phone(phoneRaw);
        String password = AccountCheck.password(passwordRaw);
        AccountCheck.confirm(password, confirm);
        acceptCode(phone, AccountCheck.code(codeRaw));
        SportUser user = new SportUser();
        user.setPhone(phone);
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setNickname(AccountCheck.mask(phone));
        user.setPasswordSalt(UserPassword.salt());
        user.setPasswordHash(UserPassword.hash(password, user.getPasswordSalt()));
        user.setCreatedAt(Instant.now().toString());
        if (!users.insert(user, password)) {
            throw new IllegalArgumentException("这个手机号已经注册");
        }
        return view(user, issue(user));
    }

    public Map<String, String> login(String phoneRaw, String passwordRaw) {
        String phone = AccountCheck.phone(phoneRaw);
        String password = AccountCheck.password(passwordRaw);
        SportUser user = users.find(phone).orElseThrow(() -> new IllegalArgumentException("手机号或密码不正确"));
        if (!UserPassword.matches(password, user.getPasswordSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("手机号或密码不正确");
        }
        return view(user, issue(user));
    }

    public Map<String, String> session(String token) {
        return view(account(token), token);
    }

    public SportUser account(String token) {
        UserToken.Session session = UserToken.read(secret, token, Instant.now().getEpochSecond());
        SportUser user = users.find(session.phone()).orElseThrow(() -> new IllegalArgumentException("请先登录"));
        if (!session.userId().equals(user.getUserId())) {
            throw new IllegalArgumentException("请先登录");
        }
        return user;
    }

    public Map<String, String> saveAvatar(String token, String image) {
        UserToken.Session session = UserToken.read(secret, token, Instant.now().getEpochSecond());
        SportUser user = users.find(session.phone()).orElseThrow(() -> new IllegalArgumentException("请先登录"));
        if (!session.userId().equals(user.getUserId())) {
            throw new IllegalArgumentException("请先登录");
        }
        byte[] jpeg = AvatarImage.jpeg(image);
        images.put(AvatarImage.objectKey(user.getUserId()), jpeg, "image/jpeg");
        user.setAvatar(AvatarImage.path(user.getUserId(), Instant.now().getEpochSecond()));
        users.saveAvatar(user.getPhone(), user.getAvatar());
        return view(user, token);
    }

    private void acceptCode(String phone, String code) {
        SmsCode saved = users.findCode(phone).orElseThrow(() -> new IllegalArgumentException("请先获取验证码"));
        if (saved.expireAt() < Instant.now().getEpochSecond()) {
            users.deleteCode(phone);
            throw new IllegalArgumentException("验证码已过期");
        }
        byte[] actual = code.getBytes(StandardCharsets.UTF_8);
        byte[] expected = saved.code().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new IllegalArgumentException("验证码不正确");
        }
        users.deleteCode(phone);
    }

    private String issue(SportUser user) {
        long expiresAt = Instant.now().plus(days, ChronoUnit.DAYS).getEpochSecond();
        return UserToken.issue(secret, user.getPhone(), user.getUserId(), expiresAt);
    }

    private static Map<String, String> view(SportUser user, String token) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("userId", user.getUserId());
        data.put("phone", user.getPhone());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        data.put("cityName", user.getCityName() == null ? "" : user.getCityName());
        data.put("token", token);
        return data;
    }

    public Map<String, String> saveCity(String token, String cityName) {
        SportUser user = account(token);
        String city = city(cityName);
        users.saveCity(user.getPhone(), city);
        user.setCityName(city);
        return view(user, token);
    }

    public static String city(String raw) {
        if (raw == null) {
            return "";
        }
        String city = raw.trim();
        if (city.length() > 20) {
            throw new IllegalArgumentException("城市名称过长");
        }
        return city;
    }
}
