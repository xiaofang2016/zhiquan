package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.api.speak.CommunityBoard;
import com.zhiquan.sport.api.speak.CommunityMatch;
import com.zhiquan.sport.api.speak.CommunitySource;
import com.zhiquan.sport.api.speak.CommunityVideo;
import com.zhiquan.sport.api.speak.Voice;
import com.zhiquan.sport.api.speak.PostRedPackService;
import com.zhiquan.sport.api.speak.SpeakService;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CommunityController {

    private final S3DatabaseStore objects;
    private final SportAwsProperties aws;
    private final ObjectMapper json;
    private final SpeakService speak;
    private final UserAuthService auth;
    private final CommunitySource source;
    private final CommunityMatch matches;
    private final PostRedPackService packets;
    private final String publicBase;

    public CommunityController(S3DatabaseStore objects,
                               SportAwsProperties aws,
                               ObjectMapper json,
                               SpeakService speak,
                               UserAuthService auth,
                               CommunitySource source,
                               CommunityMatch matches,
                               PostRedPackService packets,
                               @Value("${sport.community.public-base:https://dgtplaoir00la.cloudfront.net}") String publicBase) {
        this.objects = objects;
        this.aws = aws;
        this.json = json;
        this.speak = speak;
        this.auth = auth;
        this.source = source;
        this.matches = matches;
        this.packets = packets;
        this.publicBase = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
    }

    @GetMapping("/prod-api/community/feed")
    public KzbResult feed(@RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer ps,
                          @RequestParam(required = false) Long circleId,
                          @RequestParam(required = false) String type,
                          @RequestParam(required = false) String before,
                          @RequestParam(required = false) String sortRule,
                          @RequestParam(required = false) String token) {
        int pageNo = page == null || page < 1 ? 1 : Math.min(page, 500);
        int size = ps == null ? 60 : ps;
        if (size < 1 || size > 60) {
            throw new IllegalArgumentException("ps 必须在 1 到 60 之间");
        }
        String viewer = viewer(token);
        ObjectNode full = speak.communityFeed(read(key("feed.json")));
        ObjectNode body = CommunityBoard.page(json, full, pageNo, size, circleId, type, before, speak.board(), viewer, feedSort(sortRule));
        matches.fill(body);
        playable(body);
        return KzbResult.ok(body);
    }

    @GetMapping("/prod-api/community/circles")
    public KzbResult circles(@RequestParam(required = false) String type) {
        JsonNode feed = read(key("feed.json"));
        if ("essence".equals(type)) {
            return KzbResult.ok(CommunityBoard.essenceCircles(json, feed));
        }
        JsonNode circles = feed.path("circles");
        return KzbResult.ok(circles.isArray() ? circles : json.createArrayNode());
    }

    @GetMapping("/prod-api/community/circles/{circleId:[0-9]{1,18}}")
    public KzbResult circle(@PathVariable String circleId) {
        long id = Long.parseLong(circleId);
        for (JsonNode circle : read(key("feed.json")).path("circles")) {
            if (circle.path("id").asLong() == id) {
                return KzbResult.ok(circle);
            }
        }
        return KzbResult.ok(null);
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}")
    public KzbResult post(@PathVariable String postId, @RequestParam(required = false) String token) {
        String viewer = viewer(token);
        ObjectNode opened = open(postId, viewer);
        if (opened == null) {
            source.fill(postId);
            opened = open(postId, viewer);
        }
        matches.fill(opened);
        playable(opened);
        return KzbResult.ok(opened);
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/redpacket")
    public KzbResult claimPacket(@PathVariable String postId, @RequestBody SpeakBody body) {
        SportUser user = auth.account(body.token());
        ObjectNode opened = open(postId, user.getUserId());
        if (opened == null) {
            source.fill(postId);
            opened = open(postId, user.getUserId());
        }
        packets.claim(user, postId, opened);
        ObjectNode after = open(postId, user.getUserId());
        ObjectNode shown = after == null ? opened : after;
        playable(shown);
        return KzbResult.ok(shown);
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/like")
    public KzbResult like(@PathVariable String postId, @RequestBody SpeakBody body) {
        String viewer = auth.account(body.token()).getUserId();
        ObjectNode flags = speak.like(viewer, postId);
        ObjectNode opened = open(postId, viewer);
        if (opened != null) {
            playable(opened);
        }
        return KzbResult.ok(opened == null ? flags : opened);
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/collect")
    public KzbResult collect(@PathVariable String postId, @RequestBody SpeakBody body) {
        String viewer = auth.account(body.token()).getUserId();
        ObjectNode flags = speak.collect(viewer, postId);
        ObjectNode opened = open(postId, viewer);
        if (opened != null) {
            playable(opened);
        }
        return KzbResult.ok(opened == null ? flags : opened);
    }

    @PostMapping("/prod-api/community/users/{userId:[0-9]{1,18}}/follow")
    public KzbResult followUser(@PathVariable String userId, @RequestBody SpeakBody body) {
        return KzbResult.ok(speak.followUser(auth.account(body.token()).getUserId(), userId));
    }

    @PostMapping("/prod-api/community/circles/{circleId:[0-9]{1,18}}/follow")
    public KzbResult followCircle(@PathVariable String circleId, @RequestBody SpeakBody body) {
        return KzbResult.ok(speak.followCircle(auth.account(body.token()).getUserId(), circleId));
    }

    @PostMapping("/prod-api/community/users/{userId:[0-9]{1,18}}/block")
    public KzbResult block(@PathVariable String userId, @RequestBody SpeakBody body) {
        return KzbResult.ok(speak.block(auth.account(body.token()).getUserId(), userId));
    }

    @PostMapping("/prod-api/community/reports")
    public KzbResult report(@RequestBody ReportBody body) {
        return KzbResult.ok(speak.report(auth.account(body.token()).getUserId(), body.targetType(), body.targetId(), body.reason()));
    }

    @PostMapping("/prod-api/community/posts")
    public KzbResult publish(@RequestBody SpeakBody body) {
        SportUser user = auth.account(body.token());
        return KzbResult.ok(speak.post(SpeakService.nickOf(user), SpeakService.headOf(user), "", body.title(), body.content()));
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/highlights")
    public KzbResult highlights(@PathVariable String postId, @RequestParam(required = false) String token) {
        if (!read(key("posts/" + postId + ".json")).path("postId").canConvertToLong()
                && !containsPost(read(key("feed.json")), postId)) {
            source.fill(postId);
        }
        return KzbResult.ok(speak.communityHighlights(read(key("posts/" + postId + "/comments.json")), postId, viewer(token)));
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/comments")
    public KzbResult comments(@PathVariable String postId,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer ps,
                              @RequestParam(required = false) String sortRule,
                              @RequestParam(required = false) String token) {
        int pageNo = page == null || page < 1 ? 1 : Math.min(page, 500);
        int size = ps == null ? 50 : ps;
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("ps 必须在 1 到 50 之间");
        }
        String viewer = viewer(token);
        if (!read(key("posts/" + postId + ".json")).path("postId").canConvertToLong()
                && !containsPost(read(key("feed.json")), postId)) {
            source.fill(postId);
        }
        return KzbResult.ok(speak.communityComments(read(key("posts/" + postId + "/comments.json")), postId, pageNo, size, viewer, commentSort(sortRule)));
    }

    @GetMapping("/prod-api/community/comments/{commentId:[0-9]{1,18}}/replies")
    public KzbResult replies(@PathVariable String commentId,
                             @RequestParam(required = false) String postId,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer ps,
                             @RequestParam(required = false) String sortRule,
                             @RequestParam(required = false) String token) {
        int pageNo = page == null || page < 1 ? 1 : Math.min(page, 500);
        int size = ps == null ? 50 : ps;
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("ps 必须在 1 到 50 之间");
        }
        String sort = replySort(sortRule);
        String post = postOf(commentId, postId);
        JsonNode stored = post == null ? json.createObjectNode() : read(key("posts/" + post + "/comments.json"));
        JsonNode comment = findStored(stored, commentId);
        int declared = comment == null ? 0 : comment.path("replyCount").asInt(0);
        int embedded = comment == null || !comment.path("replies").isArray() ? 0 : comment.path("replies").size();
        JsonNode fetched = declared > embedded ? source.moreReplies(commentId) : json.createArrayNode();
        return KzbResult.ok(speak.communityReplies(stored, post == null ? "" : post, commentId, fetched, pageNo, size, sort, viewer(token)));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/comments")
    public KzbResult reply(@PathVariable String postId, @RequestBody SpeakBody body) {
        SportUser user = auth.account(body.token());
        if (body.commentId() != null) {
            String commentId = Voice.postId(Long.toString(body.commentId()));
            JsonNode stored = read(key("posts/" + postId + "/comments.json"));
            String parent = CommunityBoard.parentName(stored, speak.board(), postId, commentId);
            if (parent == null) {
                throw new IllegalArgumentException("这条评论不存在");
            }
            String toUser = null;
            String toName = parent;
            if (body.replyToUserId() != null) {
                toUser = Voice.postId(Long.toString(body.replyToUserId()));
                toName = CommunityBoard.replyName(stored, speak.board(), postId, commentId, toUser);
            }
            return KzbResult.ok(speak.reply(postId, commentId, toUser, toName, SpeakService.nickOf(user), SpeakService.headOf(user), "", body.content(), body.imageUrls()));
        }
        return KzbResult.ok(speak.comment(postId, SpeakService.nickOf(user), SpeakService.headOf(user), "", body.content(), body.imageUrls()));
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

    public record SpeakBody(String token, String title, String content, Long commentId, Long replyToUserId, List<String> imageUrls) {
    }

    public record ReportBody(String token, String targetType, String targetId, String reason) {
    }

    private ObjectNode open(String postId, String viewer) {
        return speak.communityPost(read(key("feed.json")), read(key("posts/" + postId + ".json")), postId, viewer);
    }

    private void playable(JsonNode body) {
        if (body == null) {
            return;
        }
        if (body.path("posts").isArray()) {
            body.path("posts").forEach(this::playablePost);
            return;
        }
        playablePost(body);
    }

    private void playablePost(JsonNode post) {
        if (!(post instanceof ObjectNode object)) {
            return;
        }
        for (JsonNode video : object.path("videos")) {
            if (video instanceof ObjectNode item) {
                String next = CommunityVideo.playable(item.path("url").asText(""), publicBase);
                if (!next.equals(item.path("url").asText(""))) {
                    item.put("url", next);
                }
            }
        }
    }

    private String postOf(String commentId, String requested) {
        if (requested != null && requested.matches("[0-9]{1,18}")) {
            return requested;
        }
        String onBoard = CommunityBoard.postOfComment(speak.board(), commentId);
        if (onBoard != null) {
            return onBoard;
        }
        String indexed = read(key("comment-index.json")).path(commentId).asText("");
        return indexed.matches("[0-9]{1,18}") ? indexed : null;
    }

    private static JsonNode findStored(JsonNode stored, String commentId) {
        for (JsonNode comment : stored.path("comments")) {
            if (commentId.equals(comment.path("commentId").asText())) {
                return comment;
            }
        }
        return null;
    }

    private static String feedSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return "publish";
        }
        if ("publish".equals(raw) || "comment".equals(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("排序不正确");
    }

    private static String commentSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return "newest";
        }
        if ("newest".equals(raw) || "oldest".equals(raw) || "like".equals(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("排序不正确");
    }

    private static String replySort(String raw) {
        if (raw == null || raw.isBlank()) {
            return "newest";
        }
        if ("newest".equals(raw) || "oldest".equals(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("排序不正确");
    }

    private String viewer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return auth.account(token).getUserId();
    }

    private static boolean containsPost(JsonNode feed, String postId) {
        for (JsonNode post : feed.path("posts")) {
            if (postId.equals(post.path("postId").asText())) {
                return true;
            }
        }
        return false;
    }

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }
}
