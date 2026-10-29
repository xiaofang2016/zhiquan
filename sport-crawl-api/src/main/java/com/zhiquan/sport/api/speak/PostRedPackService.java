package com.zhiquan.sport.api.speak;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhiquan.sport.api.user.WalletBook;
import com.zhiquan.sport.store.entity.SportUser;
import org.springframework.stereotype.Service;

@Service
public class PostRedPackService {

    private final SpeakService speak;
    private final WalletBook book;

    public PostRedPackService(SpeakService speak, WalletBook book) {
        this.speak = speak;
        this.book = book;
    }

    public long claim(SportUser user, String postId, JsonNode post) {
        if (post == null || post.isNull()) {
            throw new IllegalArgumentException("帖子不存在");
        }
        if (!PostRedPack.offered(post)) {
            throw new IllegalArgumentException("这篇没有红包");
        }
        long cent = speak.takePacket(user.getUserId(), postId);
        if (book.postRedpack(user, cent, user.getUserId() + "-post-" + postId)) {
            user.setBalanceCent(user.getBalanceCent() + cent);
        }
        return cent;
    }
}
