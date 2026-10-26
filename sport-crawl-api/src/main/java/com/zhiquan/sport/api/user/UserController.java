package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserAuthService auth;
    private final JxtBindService jxt;

    public UserController(UserAuthService auth, JxtBindService jxt) {
        this.auth = auth;
        this.jxt = jxt;
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
}
