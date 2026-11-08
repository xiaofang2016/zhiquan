package com.zhiquan.sport.worker;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 未来日期的列表里，判断这一页是不是已经晚于要抓的那一天。 */
public final class MatchDayPages {

    private MatchDayPages() {
    }

    public static boolean has(LocalDate day, JsonNode data) {
        for (JsonNode item : rows(data)) {
            if (day.equals(dayOf(item))) {
                return true;
            }
        }
        return false;
    }

    /** 这一页每一场都能读出开赛日，而且都晚于这一天。夹着没日期的场次时不当作已经翻过去。 */
    public static boolean past(LocalDate day, JsonNode data) {
        boolean any = false;
        for (JsonNode item : rows(data)) {
            LocalDate matchDay = dayOf(item);
            if (matchDay == null) {
                return false;
            }
            any = true;
            if (!matchDay.isAfter(day)) {
                return false;
            }
        }
        return any;
    }

    private static Iterable<JsonNode> rows(JsonNode data) {
        java.util.ArrayList<JsonNode> rows = new java.util.ArrayList<>();
        add(rows, data, "notopList");
        add(rows, data, "topList");
        add(rows, data, "dataList");
        return rows;
    }

    private static void add(java.util.List<JsonNode> rows, JsonNode data, String field) {
        JsonNode list = data == null ? null : data.get(field);
        if (list != null && list.isArray()) {
            list.forEach(rows::add);
        }
    }

    private static LocalDate dayOf(JsonNode item) {
        String text = item.path("matchtime").asText("");
        if (text.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(text.substring(0, 10));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
