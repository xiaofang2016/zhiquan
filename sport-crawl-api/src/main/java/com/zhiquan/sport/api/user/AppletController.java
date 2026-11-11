package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppletController {

    @PostMapping("/prod-api/applet/list")
    public KzbResult list(@RequestBody(required = false) AppletBody body) {
        String client = body == null ? "" : body.client();
        return KzbResult.ok(AppletCatalog.list(client));
    }

    public record AppletBody(String client) {
    }
}
