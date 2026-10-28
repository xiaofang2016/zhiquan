package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.api.speak.SpeakService;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommunityController {

    private final S3DatabaseStore objects;
    private final SportAwsProperties aws;
    private final ObjectMapper json;
    private final SpeakService speak;
    private final UserAuthService auth;

    public CommunityController(S3DatabaseStore objects,
                               SportAwsProperties aws,
                               ObjectMapper json,
                               SpeakService speak,
                               UserAuthService auth) {
        this.objects = objects;
        this.aws = aws;
        this.json = json;
        this.speak = speak;
        this.auth = auth;
    }

    @GetMapping("/prod-api/community/feed")
    public KzbResult feed() {
        return KzbResult.ok(speak.communityFeed(read(key("feed.json"))));
    }

    @PostMapping("/prod-api/community/posts")
    public KzbResult publish(@RequestBody SpeakBody body) {
        SportUser user = auth.account(body.token());
        return KzbResult.ok(speak.post(SpeakService.nickOf(user), SpeakService.headOf(user), "", body.title(), body.content()));
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/comments")
    public KzbResult comments(@PathVariable String postId) {
        return KzbResult.ok(speak.communityComments(read(key("posts/" + postId + "/comments.json")), postId));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/comments")
    public KzbResult reply(@PathVariable String postId, @RequestBody SpeakBody body) {
        SportUser user = auth.account(body.token());
        return KzbResult.ok(speak.comment(postId, SpeakService.nickOf(user), SpeakService.headOf(user), "", body.content()));
    }

    private JsonNode read(String key) {
        byte[] body = objects.get(key);
        if (body == null || body.length == 0) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(body);
        } catch (Exception ex) {
            return json.createObjectNode();
        }
    }

    public record SpeakBody(String token, String title, String content) {
    }

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
