package com.zhiquan.sport.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhiquan.sport.api.speak.CommunityLife;
import com.zhiquan.sport.api.speak.CommunityPicture;
import com.zhiquan.sport.api.speak.CommunityUpload;
import com.zhiquan.sport.api.speak.SpeakService;
import com.zhiquan.sport.api.user.UserAuthService;
import com.zhiquan.sport.api.user.WalletBook;
import com.zhiquan.sport.store.aws.SportAwsProperties;
import com.zhiquan.sport.store.entity.SportUser;
import com.zhiquan.sport.store.repo.S3DatabaseStore;
import com.zhiquan.sport.store.repo.S3ImageStore;
import com.zhiquan.sport.store.repo.SportUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
public class CommunityMeController {

    private final S3DatabaseStore objects;
    private final S3ImageStore images;
    private final SportAwsProperties aws;
    private final ObjectMapper json;
    private final SpeakService speak;
    private final UserAuthService auth;
    private final SportUserRepository users;
    private final WalletBook book;
    private final String publicBase;

    public CommunityMeController(S3DatabaseStore objects,
                                 S3ImageStore images,
                                 SportAwsProperties aws,
                                 ObjectMapper json,
                                 SpeakService speak,
                                 UserAuthService auth,
                                 SportUserRepository users,
                                 WalletBook book,
                                 @Value("${sport.community.public-base:https://dgtplaoir00la.cloudfront.net}") String publicBase) {
        this.objects = objects;
        this.images = images;
        this.aws = aws;
        this.json = json;
        this.speak = speak;
        this.auth = auth;
        this.users = users;
        this.book = book;
        this.publicBase = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
    }

    @PostMapping("/prod-api/community/comments/{commentId:[0-9]{1,18}}/like")
    public KzbResult likeComment(@PathVariable String commentId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        int base = originalLikes(body.postId(), commentId, commentId, false);
        return ready(speak.likeComment(user.getUserId(), commentId, SpeakService.nickOf(user), head(user), body.postId(), base));
    }

    @PostMapping("/prod-api/community/replies/{replyId:[0-9]{1,18}}/like")
    public KzbResult likeReply(@PathVariable String replyId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        int base = originalLikes(body.postId(), body.commentId(), replyId, true);
        return ready(speak.likeReply(user.getUserId(), replyId, SpeakService.nickOf(user), head(user), body.postId(), body.commentId(), base));
    }

    @PostMapping("/prod-api/community/comments/{commentId:[0-9]{1,18}}/remove")
    public KzbResult removeComment(@PathVariable String commentId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        if (body.postId() == null || body.postId().isBlank()) {
            throw new IllegalArgumentException("帖子编号不正确");
        }
        return KzbResult.ok(speak.removeComment(user.getUserId(), body.postId(), commentId));
    }

    @PostMapping("/prod-api/community/replies/{replyId:[0-9]{1,18}}/remove")
    public KzbResult removeReply(@PathVariable String replyId, @RequestBody TokenBody body) {
        return KzbResult.ok(speak.removeReply(auth.account(body.token()).getUserId(), replyId));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/edit")
    public KzbResult edit(@PathVariable String postId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        return ready(speak.editPost(user.getUserId(), postId, body.title(), body.content(), body.circleIds(), body.pictures(), body.videos(), body.topicIds(), body.mentionUserIds(), body.matchId(), body.matchType()));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/remove")
    public KzbResult removePost(@PathVariable String postId, @RequestBody TokenBody body) {
        return KzbResult.ok(speak.removePost(auth.account(body.token()).getUserId(), postId));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/visibility")
    public KzbResult visibility(@PathVariable String postId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        return ready(speak.visibility(user.getUserId(), postId, body.visibility()));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/reward-switch")
    public KzbResult rewardSwitch(@PathVariable String postId, @RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        return ready(speak.rewardSwitch(user.getUserId(), postId));
    }

    @PostMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/reward")
    public KzbResult reward(@PathVariable String postId, @RequestBody TokenBody body) {
        SportUser from = auth.account(body.token());
        long cents = CommunityLife.cents(body.amount());
        ObjectNode post = speak.communityPost(read(key("feed.json")), read(key("posts/" + postId + ".json")), postId, from.getUserId());
        if (post == null) {
            throw new IllegalArgumentException("帖子不存在");
        }
        if (!post.path("rewardEnabled").asBoolean(false)) {
            throw new IllegalArgumentException("这篇帖子没有打开打赏");
        }
        if (from.getUserId().equals(post.path("userId").asText(""))) {
            throw new IllegalArgumentException("不能打赏自己的帖子");
        }
        String phone = speak.authorPhone(postId);
        if (phone.isBlank() || phone.equals(from.getPhone())) {
            throw new IllegalArgumentException("暂时不能打赏原站作者");
        }
        SportUser to = users.find(phone).orElseThrow(() -> new IllegalArgumentException("暂时不能打赏原站作者"));
        String orderNo = UUID.randomUUID().toString().replace("-", "");
        book.reward(from, to, cents, orderNo);
        try {
            speak.commitReward(postId, from.getUserId(), SpeakService.nickOf(from), head(from), cents, body.showHead() == null || body.showHead());
        } catch (RuntimeException ex) {
            book.reward(to, from, cents, orderNo + "-back");
            throw new IllegalArgumentException("打赏失败，余额已退回");
        }
        ObjectNode again = speak.communityPost(read(key("feed.json")), read(key("posts/" + postId + ".json")), postId, from.getUserId());
        return ready(again == null ? post : again);
    }

    @GetMapping("/prod-api/community/posts/{postId:[0-9]{1,18}}/rewards")
    public KzbResult rewards(@PathVariable String postId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        return ready(speak.rewardList(postId, pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/users/{userId:[0-9a-fA-F]{1,32}}")
    public KzbResult user(@PathVariable String userId, @RequestParam(required = false) String token) {
        return ready(speak.profile(read(key("feed.json")), userId, viewer(token)));
    }

    @GetMapping("/prod-api/community/users/{userId:[0-9a-fA-F]{1,32}}/comments")
    public KzbResult commentsOf(@PathVariable String userId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        return ready(speak.myComments(read(key("feed.json")), read(key("comment-index.json")), userId, pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/users/{userId:[0-9a-fA-F]{1,32}}/follows")
    public KzbResult follows(@PathVariable String userId, @RequestParam(required = false) String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        return ready(speak.people(read(key("feed.json")), userId, viewer(token), false, pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/users/{userId:[0-9a-fA-F]{1,32}}/fans")
    public KzbResult fans(@PathVariable String userId, @RequestParam(required = false) String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        return ready(speak.people(read(key("feed.json")), userId, viewer(token), true, pageOf(page), sizeOf(ps, 20)));
    }

    @PostMapping("/prod-api/community/me/profile")
    public KzbResult profile(@RequestBody TokenBody body) {
        SportUser user = auth.account(body.token());
        if (body.cityName() != null) {
            auth.saveCity(body.token(), body.cityName());
        }
        speak.saveProfile(user.getUserId(), body.intro(), body.bg(), body.cityName());
        return ready(speak.profile(read(key("feed.json")), user.getUserId(), user.getUserId()));
    }

    @GetMapping("/prod-api/community/me/collects")
    public KzbResult collects(@RequestParam String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        SportUser user = auth.account(token);
        return ready(speak.myPosts(read(key("feed.json")), user.getUserId(), "collects", pageOf(page), sizeOf(ps, 20)));
    }

    @PostMapping("/prod-api/community/me/collects/clear")
    public KzbResult clearCollects(@RequestBody TokenBody body) {
        return KzbResult.ok(speak.clearMine(auth.account(body.token()).getUserId(), "collects"));
    }

    @GetMapping("/prod-api/community/me/likes")
    public KzbResult likes(@RequestParam String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        SportUser user = auth.account(token);
        return ready(speak.myPosts(read(key("feed.json")), user.getUserId(), "likes", pageOf(page), sizeOf(ps, 20)));
    }

    @PostMapping("/prod-api/community/me/likes/clear")
    public KzbResult clearLikes(@RequestBody TokenBody body) {
        return KzbResult.ok(speak.clearMine(auth.account(body.token()).getUserId(), "likes"));
    }

    @GetMapping("/prod-api/community/me/comments")
    public KzbResult comments(@RequestParam String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        SportUser user = auth.account(token);
        return ready(speak.myComments(read(key("feed.json")), read(key("comment-index.json")), user.getUserId(), pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/me/circles")
    public KzbResult circles(@RequestParam String token) {
        SportUser user = auth.account(token);
        return ready(speak.myCircles(read(key("feed.json")), user.getUserId()));
    }

    @GetMapping("/prod-api/community/me/blocks")
    public KzbResult blocks(@RequestParam String token, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        SportUser user = auth.account(token);
        return ready(speak.myBlocks(read(key("feed.json")), user.getUserId(), pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/me/messages")
    public KzbResult messages(@RequestParam String token, @RequestParam(required = false) String type, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer ps) {
        SportUser user = auth.account(token);
        return ready(speak.messages(user.getUserId(), type, pageOf(page), sizeOf(ps, 20)));
    }

    @GetMapping("/prod-api/community/me/messages/unread")
    public KzbResult unread(@RequestParam String token) {
        return KzbResult.ok(speak.unread(auth.account(token).getUserId()));
    }

    @PostMapping("/prod-api/community/me/messages/read")
    public KzbResult readMessages(@RequestBody TokenBody body) {
        return KzbResult.ok(speak.readMessages(auth.account(body.token()).getUserId(), body.ids()));
    }

    @PostMapping("/prod-api/community/upload")
    public KzbResult upload(@RequestBody TokenBody body) {
        auth.account(body.token());
        CommunityUpload.File file = CommunityUpload.read(body.image());
        String id = UUID.randomUUID().toString().replace("-", "");
        images.put("community/media/" + id + file.extension(), file.bytes(), file.contentType());
        ObjectNode data = json.createObjectNode();
        data.put("url", publicBase + "/prod-api/community/media/" + id);
        data.put("type", file.video() ? "video" : "image");
        if (file.video()) {
            data.putNull("w");
            data.putNull("h");
        } else {
            int[] size = CommunityPicture.size(file.bytes());
            if (size[0] > 0) {
                data.put("w", size[0]);
                data.put("h", size[1]);
            } else {
                data.putNull("w");
                data.putNull("h");
            }
        }
        return KzbResult.ok(data);
    }

    @GetMapping("/prod-api/community/media/{id:[a-f0-9]{32}}")
    public ResponseEntity<byte[]> media(@PathVariable String id) {
        byte[] jpeg = mediaBytes("community/media/" + id + ".jpg");
        if (jpeg != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                    .body(jpeg);
        }
        byte[] video = mediaBytes("community/media/" + id + ".mp4");
        if (video == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(video);
    }

    private byte[] mediaBytes(String key) {
        try {
            byte[] body = images.get(key);
            return body == null || body.length == 0 ? null : body;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @GetMapping("/prod-api/community/topics/{topicId:[0-9]{1,18}}")
    public KzbResult topic(@PathVariable long topicId) {
        ObjectNode topic = speak.topic(read(key("feed.json")), topicId);
        if (topic == null) {
            return KzbResult.ok(null);
        }
        return ready(topic);
    }

    @GetMapping("/prod-api/community/announces/{announceId:[0-9]{1,18}}")
    public KzbResult announce(@PathVariable long announceId) {
        ObjectNode announce = speak.announce(read(key("feed.json")), announceId);
        if (announce == null) {
            return KzbResult.ok(null);
        }
        return ready(announce);
    }

    public record TokenBody(String token, String postId, String commentId, String title, String content, String amount, String visibility, String intro, String bg, String image, List<String> ids, List<Long> circleIds, List<String> pictures, List<String> videos, List<Long> topicIds, List<String> mentionUserIds, Long matchId, String matchType, String cityName, Boolean showHead) {
    }

    private int originalLikes(String postId, String commentId, String id, boolean reply) {
        String post = postId != null && postId.matches("[0-9]{1,18}") ? postId : "";
        if (post.isEmpty()) {
            String key = commentId != null && commentId.matches("[0-9]{1,18}") ? commentId : id;
            String indexed = read(key("comment-index.json")).path(key).asText("");
            post = indexed.matches("[0-9]{1,18}") ? indexed : "";
        }
        JsonNode upstream = post.isEmpty() ? json.createObjectNode() : read(key("posts/" + post + "/comments.json"));
        return CommunityLife.originalLikes(upstream, speak.board(), id, reply);
    }

    private String viewer(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return auth.account(token).getUserId();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String head(SportUser user) {
        return SpeakService.absoluteHead(SpeakService.headOf(user), publicBase);
    }

    private KzbResult ready(JsonNode body) {
        com.zhiquan.sport.api.speak.CommunityBoard.absoluteHeads(body, publicBase);
        return KzbResult.ok(body);
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

    private String key(String name) {
        String prefix = aws.getCommunityPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "community/";
        }
        return prefix.endsWith("/") ? prefix + name : prefix + "/" + name;
    }

    private static int pageOf(Integer page) {
        return page == null || page < 1 ? 1 : Math.min(page, 500);
    }

    private static int sizeOf(Integer ps, int fallback) {
        int size = ps == null ? fallback : ps;
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("ps 必须在 1 到 50 之间");
        }
        return size;
    }
}
