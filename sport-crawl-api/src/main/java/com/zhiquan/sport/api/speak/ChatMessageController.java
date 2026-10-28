package com.zhiquan.sport.api.speak;

import com.zhiquan.sport.api.KzbResult;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.store.entity.SportUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatMessageController {

    private final SpeakService speak;
    private final UserAuthService auth;

    public ChatMessageController(SpeakService speak, UserAuthService auth) {
        this.speak = speak;
        this.auth = auth;
    }

    @PostMapping("/prod-api/chat/messages")
    public KzbResult speak(@RequestBody ChatBody body) {
        SportUser user = auth.account(body.token());
        return KzbResult.ok(speak.speak(body.mid(), SpeakService.nickOf(user), body.content()));
    }

    public record ChatBody(String token, String mid, String content) {
    }
}
