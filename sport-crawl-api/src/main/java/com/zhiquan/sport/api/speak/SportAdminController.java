package com.zhiquan.sport.api.speak;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SportAdminController {

    private final SpeakService speak;

    public SportAdminController(SpeakService speak) {
        this.speak = speak;
    }

    @PostMapping("/prod-api/admin/chat")
    public KzbResult chat(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                          @RequestBody AdminChat body) {
        speak.checkAdmin(key);
        return KzbResult.ok(speak.speak(body.mid(), body.nick(), body.content()));
    }

    @PostMapping("/prod-api/admin/community/posts")
    public KzbResult post(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                          @RequestBody AdminPost body) {
        speak.checkAdmin(key);
        return KzbResult.ok(speak.post(body.nick(), body.head(), body.badgeName(), body.title(), body.content()));
    }

    @PostMapping("/prod-api/admin/community/comments")
    public KzbResult comment(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                             @RequestBody AdminComment body) {
        speak.checkAdmin(key);
        return KzbResult.ok(speak.comment(body.postId(), body.nick(), body.head(), body.badgeName(), body.content()));
    }

    public record AdminChat(String mid, String nick, String content) {
    }

    public record AdminPost(String nick, String head, String badgeName, String title, String content) {
    }

    public record AdminComment(String postId, String nick, String head, String badgeName, String content) {
    }
}
