package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class CommunityController {

    private final S3DatabaseStore objects;
    private final SportAwsProperties aws;
    private final ObjectMapper json;

    public CommunityController(S3DatabaseStore objects, SportAwsProperties aws, ObjectMapper json) {
        this.objects = objects;
        this.aws = aws;
        this.json = json;
    }

    @GetMapping("/prod-api/community/feed")
    public KzbResult feed() {
        return KzbResult.ok(read(key("feed.json"), Map.of("circles", List.of(), "posts", List.of())));
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/comments")
    public KzbResult comments(@PathVariable String postId) {
        return KzbResult.ok(read(key("posts/" + postId + "/comments.json"), Map.of("comments", List.of())));
    }

    private Object read(String key, Object empty) {
        byte[] body = objects.get(key);
        if (body == null || body.length == 0) {
            return empty;
        }
        try {
            return json.readTree(body);
        } catch (Exception ex) {
            return empty;
        }
    }

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
