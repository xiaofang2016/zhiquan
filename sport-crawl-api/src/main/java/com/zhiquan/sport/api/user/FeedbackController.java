package com.zhiquan.sport.api.user;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FeedbackController {

    private final FeedbackService feedback;

    public FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @PostMapping("/prod-api/user/feedback")
    public KzbResult add(@RequestBody FeedbackBody body) {
        return KzbResult.ok(feedback.add(body.token(), body.content(), body.contact(), body.images()));
    }

    @PostMapping("/prod-api/user/feedback/list")
    public KzbResult list(@RequestBody FeedbackBody body) {
        return KzbResult.ok(feedback.list(body.token()));
    }

    public record FeedbackBody(String token, String content, String contact, List<String> images) {
    }
}
