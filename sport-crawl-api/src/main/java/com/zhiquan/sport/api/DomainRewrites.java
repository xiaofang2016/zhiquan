package com.zhiquan.sport.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动时读入的域名对。左边整段换成右边，大小写都认。同一左边以最后一行为准。
 */
@Component
public class DomainRewrites {

    private static final Logger log = LoggerFactory.getLogger(DomainRewrites.class);
    private static final String DEFAULT = "JX8.com=JX8.CC\nqiumishuo.com=qiumishuo.cc";
    private static final Pattern NAME = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+",
            Pattern.CASE_INSENSITIVE);

    private final List<Pair> pairs;
    private final Pattern pattern;
    private final Map<String, String> targets;

    @Autowired
    public DomainRewrites(@Value("${sport.domain-rewrites:}") String inline,
                          @Value("${sport.domain-rewrites-file:/etc/sport-crawl/domain-rewrites}") String file) {
        this(source(inline, file));
    }

    DomainRewrites(String text) {
        Map<String, Pair> byName = new LinkedHashMap<>();
        if (text == null) {
            text = DEFAULT;
        }
        for (String piece : pieces(text)) {
            int split = piece.indexOf('=');
            if (split <= 0) {
                log.warn("域名替换跳过: {}", piece);
                continue;
            }
            String from = piece.substring(0, split).trim();
            String to = piece.substring(split + 1).trim();
            if (!domain(from) || !domain(to)) {
                log.warn("域名替换跳过: {}", piece);
                continue;
            }
            byName.put(from.toLowerCase(Locale.ROOT), new Pair(from, to));
        }
        List<Pair> ordered = new ArrayList<>(byName.values());
        ordered.sort((left, right) -> Integer.compare(right.from.length(), left.from.length()));
        this.pairs = List.copyOf(ordered);
        this.targets = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder();
        for (Pair pair : pairs) {
            if (!body.isEmpty()) {
                body.append('|');
            }
            body.append(Pattern.quote(pair.from));
            targets.put(pair.from.toLowerCase(Locale.ROOT), pair.to);
        }
        this.pattern = pairs.isEmpty()
                ? null
                : Pattern.compile("(?<![A-Za-z0-9.-])(?:" + body + ")(?![A-Za-z0-9.-])", Pattern.CASE_INSENSITIVE);
        log.info("域名替换 {} 对", pairs.size());
    }

    public List<Map<String, String>> rows() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (Pair pair : pairs) {
            rows.add(Map.of("from", pair.from, "to", pair.to));
        }
        return rows;
    }

    public byte[] apply(byte[] body) {
        if (body == null || body.length == 0 || pattern == null) {
            return body;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        String next = apply(text);
        if (next.equals(text)) {
            return body;
        }
        return next.getBytes(StandardCharsets.UTF_8);
    }

    public String apply(String text) {
        if (text == null || text.isEmpty() || pattern == null) {
            return text;
        }
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String target = targets.get(matcher.group().toLowerCase(Locale.ROOT));
            matcher.appendReplacement(out, Matcher.quoteReplacement(target == null ? matcher.group() : target));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static boolean textual(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }
        if (type.startsWith("text/")) {
            return true;
        }
        return "application/json".equals(type)
                || type.endsWith("+json")
                || "application/javascript".equals(type)
                || "application/xml".equals(type)
                || "application/vnd.apple.mpegurl".equals(type);
    }

    private static String source(String inline, String file) {
        if (inline != null && !inline.isBlank()) {
            return inline;
        }
        if (file == null || file.isBlank()) {
            return null;
        }
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            log.warn("域名名单没有读到: {}", ex.getMessage());
            return null;
        }
    }

    private static List<String> pieces(String text) {
        List<String> pieces = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            for (String piece : trimmed.split(",")) {
                String item = piece.trim();
                if (!item.isEmpty()) {
                    pieces.add(item);
                }
            }
        }
        return pieces;
    }

    private static boolean domain(String name) {
        return name != null && name.length() <= 253 && NAME.matcher(name).matches();
    }

    private record Pair(String from, String to) {
    }
}
