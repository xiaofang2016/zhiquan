package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import com.zhiquan.sport.store.repo.S3ImageStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class UserController {

    private final UserAuthService auth;
    private final S3ImageStore images;
    private final JxtBindService jxt;
    private final UedBindService ued;
    private final MissionService mission;

    public UserController(UserAuthService auth, S3ImageStore images, JxtBindService jxt, UedBindService ued, MissionService mission) {
        this.auth = auth;
        this.images = images;
        this.jxt = jxt;
        this.ued = ued;
        this.mission = mission;
    }

    @PostMapping("/prod-api/user/sms")
    public KzbResult sms(@RequestBody UserRequest body) {
        auth.sendCode(body.phone());
        return KzbResult.ok(null);
    }

    @PostMapping("/prod-api/user/register")
    public KzbResult register(@RequestBody UserRequest body) {
        return KzbResult.ok(auth.register(body.phone(), body.password(), body.confirm(), body.code()));
    }

    @PostMapping("/prod-api/user/login")
    public KzbResult login(@RequestBody UserRequest body) {
        return KzbResult.ok(auth.login(body.phone(), body.password()));
    }

    @PostMapping("/prod-api/user/session")
    public KzbResult session(@RequestBody UserRequest body) {
        return KzbResult.ok(auth.session(body.token()));
    }

    @PostMapping("/prod-api/user/mission")
    public KzbResult mission(@RequestBody UserRequest body) {
        return KzbResult.ok(mission.state(body.token()));
    }

    @PostMapping("/prod-api/user/mission/sign")
    public KzbResult missionSign(@RequestBody UserRequest body) {
        return KzbResult.ok(mission.sign(body.token(), body.day() == null ? 0 : body.day()));
    }

    @PostMapping("/prod-api/user/mission/claim")
    public KzbResult missionClaim(@RequestBody UserRequest body) {
        return KzbResult.ok(mission.claim(body.token(), body.type() == null ? 0 : body.type()));
    }

    @PostMapping("/prod-api/user/avatar")
    public KzbResult avatar(@RequestBody UserRequest body) {
        return KzbResult.ok(auth.saveAvatar(body.token(), body.image()));
    }

    @GetMapping("/prod-api/user/avatar/{userId:[a-f0-9]{32}}")
    public ResponseEntity<byte[]> avatarFile(@PathVariable String userId) {
        byte[] body;
        try {
            body = images.get(AvatarImage.objectKey(userId));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(body);
    }

    @PostMapping("/prod-api/user/jxt/status")
    public KzbResult jxtStatus(@RequestBody UserRequest body) {
        return KzbResult.ok(jxt.status(body.token()));
    }

    @PostMapping("/prod-api/user/jxt/sms")
    public KzbResult jxtSms(@RequestBody UserRequest body) {
        jxt.sms(body.token());
        return KzbResult.ok(null);
    }

    @PostMapping("/prod-api/user/jxt/bind")
    public KzbResult jxtBind(@RequestBody UserRequest body) {
        return KzbResult.ok(jxt.confirm(body.token(), body.code()));
    }

    @PostMapping("/prod-api/user/jxt/unbind")
    public KzbResult jxtUnbind(@RequestBody UserRequest body) {
        return KzbResult.ok(jxt.unbind(body.token()));
    }

    @PostMapping("/prod-api/user/jxt/withdraw")
    public KzbResult jxtWithdraw(@RequestBody UserRequest body) {
        return KzbResult.ok(jxt.withdraw(body.token(), body.amount()));
    }

    @PostMapping("/prod-api/user/ued/status")
    public KzbResult uedStatus(@RequestBody UserRequest body) {
        return KzbResult.ok(ued.status(body.token()));
    }

    @PostMapping("/prod-api/user/ued/sms")
    public KzbResult uedSms(@RequestBody UserRequest body) {
        ued.sms(body.token());
        return KzbResult.ok(null);
    }

    @PostMapping("/prod-api/user/ued/bind")
    public KzbResult uedBind(@RequestBody UserRequest body) {
        return KzbResult.ok(ued.confirm(body.token(), body.code()));
    }

    @PostMapping("/prod-api/user/ued/unbind")
    public KzbResult uedUnbind(@RequestBody UserRequest body) {
        return KzbResult.ok(ued.unbind(body.token()));
    }

    @PostMapping("/prod-api/user/ued/withdraw")
    public KzbResult uedWithdraw(@RequestBody UserRequest body) {
        return KzbResult.ok(ued.withdraw(body.token(), body.amount()));
    }
}
