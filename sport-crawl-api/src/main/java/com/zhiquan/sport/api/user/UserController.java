package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserAuthService auth;

    public UserController(UserAuthService auth) {
        this.auth = auth;
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
}
