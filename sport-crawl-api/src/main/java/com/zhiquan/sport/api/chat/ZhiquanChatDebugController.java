package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.KzbResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ZhiquanChatDebugController {

    private final ZhiquanChatDebugService chat;

    public ZhiquanChatDebugController(ZhiquanChatDebugService chat) {
        this.chat = chat;
    }

    @GetMapping("/prod-api/chat/groups")
    public KzbResult groups() {
        return KzbResult.ok(chat.groups());
    }

    @GetMapping("/prod-api/chat/groups/{groupId:[0-9]{1,18}}/messages")
    public KzbResult messages(@PathVariable long groupId,
                              @RequestParam(value = "before", required = false) String before) {
        return KzbResult.ok(chat.messages(groupId, before));
    }
}
