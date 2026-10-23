package com.zhiquan.sport.chat;

import com.zhiquan.sport.store.chat.ChatDocument;
import com.zhiquan.sport.store.chat.ChatTranscript;
import com.zhiquan.sport.store.repo.S3ChatStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatSyncService {

    private final S3ChatStore store;
    private final String upstream;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public ChatSyncService(S3ChatStore store, @Value("${sport.chat.upstream}") String upstream) {
        this.store = store;
        this.upstream = upstream;
    }

    public ChatDocument open(String matchId, int type) {
        String id = ChatTranscript.matchId(matchId);
        synchronized (locks.computeIfAbsent(id, key -> new Object())) {
            UpstreamRoom room = pull(id, type);
            if (!room.messages().isEmpty()) {
                store.append(id, room.roomId(), room.messages());
            }
            return store.read(id);
        }
    }

    private UpstreamRoom pull(String matchId, int type) {
        String url = upstream + "/prod-api/words/wangyi/match/chatroom?pid=4&apptype=0&type="
                + type + "&mid=" + matchId + "&num=-2";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://kzb001.net/")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new UpstreamRoom(null, java.util.List.of());
            }
            return UpstreamRoom.parse(response.body());
        } catch (Exception ex) {
            return new UpstreamRoom(null, java.util.List.of());
        }
    }
}
