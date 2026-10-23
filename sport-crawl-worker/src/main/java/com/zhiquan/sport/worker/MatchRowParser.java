package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiquan.sport.store.ImageFields;
import com.zhiquan.sport.store.ListSection;
import com.zhiquan.sport.store.entity.SportMatch;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class MatchRowParser {

    private static final DateTimeFormatter MATCH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public MatchRowParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SportMatch toRow(JsonNode item, LocalDate listDate, String section, int pageNumber, int index) {
        if (item == null || !item.isObject() || !item.hasNonNull("id")) {
            return null;
        }
        long sourceId;
        try {
            sourceId = item.get("id").isNumber()
                    ? item.get("id").asLong()
                    : Long.parseLong(item.get("id").asText());
        } catch (NumberFormatException ex) {
            return null;
        }
        SportMatch row = new SportMatch();
        row.setSourceId(sourceId);
        row.setCid(numberOrNull(item, "cid"));
        row.setSportType(numberOrNull(item, "type"));
        row.setListDate(listDate);
        row.setMatchTime(matchTime(item.path("matchtime").asText("")));
        row.setIsHot(item.path("isHot").asInt(0) == 1 ? 1 : 0);
        row.setListSection(section);
        row.setSortOrder(ListSection.DATA.equals(section) ? pageNumber * 100_000 + index : index);
        try {
            row.setPayload(objectMapper.writeValueAsString(item));
        } catch (Exception ex) {
            throw new IllegalStateException("match payload " + sourceId, ex);
        }
        return row;
    }

    public static java.util.List<String> imageUrls(JsonNode item) {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        if (item == null || !item.isObject()) {
            return urls;
        }
        for (String field : ImageFields.NAMES) {
            String url = item.path(field).asText("").trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static Integer numberOrNull(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    private static LocalDateTime matchTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, MATCH_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
