package com.zhiquan.sport.api.chat;

import com.zhiquan.sport.api.KzbResult;
import com.zhiquan.sport.api.speak.AdminKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatRedpackController {

    private final ChatRedpackService packets;
    private final String adminKey;

    public ChatRedpackController(ChatRedpackService packets, @Value("${sport.admin.key:}") String adminKey) {
        this.packets = packets;
        this.adminKey = adminKey;
    }

    @PostMapping("/prod-api/chat/groups/{groupId:[0-9]{1,18}}/redpacket")
    public KzbResult claim(@PathVariable long groupId, @RequestBody Claim body) {
        return KzbResult.ok(packets.claim(groupId, body.token(), body.id()));
    }

    @PostMapping("/prod-api/admin/chat-redpack")
    public KzbResult range(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                           @RequestBody Range body) {
        AdminKey.check(adminKey, key);
        return KzbResult.ok(packets.saveRange(body.minCent(), body.maxCent()));
    }

    public record Claim(String token, String id) {
    }

    public record Range(int minCent, int maxCent) {
    }
}
