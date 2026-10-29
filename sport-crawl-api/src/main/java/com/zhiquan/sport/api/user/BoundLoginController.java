package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class BoundLoginController {

    private final BoundLoginService login;

    public BoundLoginController(BoundLoginService login) {
        this.login = login;
    }

    @PostMapping("/prod-api/user/bound-login")
    public KzbResult open(HttpServletRequest request) throws java.io.IOException {
        String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return KzbResult.ok(login.open(raw));
    }
}
