package com.zhiquan.sport.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
public class SportController {

    private final SportQueryService queries;

    public SportController(SportQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/sport/health")
    public ApiResult health() {
        return ApiResult.ok("ok");
    }

    @GetMapping("/api/sport/leagues")
    public ApiResult leagues() {
        return ApiResult.ok(queries.leagues());
    }

    @GetMapping("/api/sport/dates")
    public ApiResult dates() {
        return ApiResult.ok(queries.dates());
    }

    @GetMapping("/api/sport/matches")
    public ApiResult matches(@RequestParam(required = false) String date,
                             @RequestParam(required = false) Integer type,
                             @RequestParam(required = false) Integer cid,
                             @RequestParam(required = false) Integer hot,
                             @RequestParam(required = false) String nextToken,
                             @RequestParam(defaultValue = "20") int size) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 不能为空");
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("date 格式应为 yyyy-MM-dd");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        return ApiResult.ok(queries.matches(parsed, type, cid, hot, size, nextToken));
    }
}
