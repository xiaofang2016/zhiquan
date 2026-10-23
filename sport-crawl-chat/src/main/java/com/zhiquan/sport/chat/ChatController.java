package com.zhiquan.sport.chat;

import com.zhiquan.sport.store.chat.ChatDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RestController
public class ChatController implements WebMvcConfigurer {

    private final ChatSyncService sync;

    public ChatController(ChatSyncService sync) {
        this.sync = sync;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @GetMapping("/chat")
    public ChatView open(@RequestParam String mid, @RequestParam(defaultValue = "1") int type) {
        ChatDocument document = sync.open(mid, type);
        return new ChatView("ok", "0", document);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*").allowedMethods("GET");
    }

    public record ChatView(String msg, String code, ChatDocument data) {
    }
}
