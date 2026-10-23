package com.zhiquan.sport.store.chat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChatTranscript {

    private ChatTranscript() {
    }

    public static String matchId(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,18}")) {
            throw new IllegalArgumentException("mid 应为比赛编号");
        }
        return raw;
    }

    /**
     * 按编号并进这份文档。初始化写入的最新 100 条、往上翻到的更早记录、之后的新发言都留在同一份里。
     * 已有编号丢掉。版本停在最新一条，下一次只从这里接着要新发言。没有新内容时 changed 为 false，调用方不要写回 S3。
     */
    public static Merge append(ChatDocument current, String roomId, List<ChatMessage> incoming) {
        String matchId = matchId(current.matchId());
        List<ChatMessage> kept = new ArrayList<>(current.messages() == null ? List.of() : current.messages());
        Set<String> seen = new LinkedHashSet<>();
        for (ChatMessage message : kept) {
            if (message.id() != null && !message.id().isBlank()) {
                seen.add(message.id());
            }
        }
        if (incoming != null) {
            for (ChatMessage message : incoming) {
                ChatMessage normalized = normalize(message);
                if (normalized == null || !seen.add(normalized.id())) {
                    continue;
                }
                kept.add(normalized);
            }
        }
        kept.sort(Comparator.comparingLong(ChatMessage::time).thenComparing(ChatMessage::id, ChatTranscript::compareId));
        boolean roomChanged = roomId != null && !roomId.isBlank() && !roomId.equals(current.roomId());
        boolean grew = kept.size() != (current.messages() == null ? 0 : current.messages().size());
        if (!grew && !roomChanged) {
            return new Merge(current, false);
        }
        ChatVersion next = current.version();
        if (!kept.isEmpty()) {
            ChatMessage tail = kept.get(kept.size() - 1);
            next = new ChatVersion(tail.time(), tail.id());
        }
        String nextRoom = roomChanged ? roomId : current.roomId();
        return new Merge(new ChatDocument(matchId, nextRoom, next, List.copyOf(kept)), true);
    }

    /** 打开时给用户看的是最新 limit 条。文档里更早的记录留着，等往上翻再拿。 */
    public static List<ChatMessage> latest(ChatDocument document, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("条数至少为 1");
        }
        List<ChatMessage> messages = document.messages() == null ? List.of() : document.messages();
        if (messages.size() <= limit) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(messages.size() - limit, messages.size()));
    }

    private static ChatMessage normalize(ChatMessage message) {
        if (message == null || message.time() < 0) {
            return null;
        }
        String nick = message.nick() == null ? "" : message.nick();
        String content = message.content() == null ? "" : message.content();
        if (content.isBlank()) {
            return null;
        }
        String id = message.id();
        if (id == null || id.isBlank()) {
            id = "h:" + sha256(nick + "\n" + message.time() + "\n" + content);
        }
        return new ChatMessage(id, message.time(), nick, content);
    }

    static int compareId(String left, String right) {
        boolean leftNumber = left.chars().allMatch(Character::isDigit);
        boolean rightNumber = right.chars().allMatch(Character::isDigit);
        if (leftNumber && rightNumber) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        return left.compareTo(right);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record Merge(ChatDocument document, boolean changed) {
    }
}
