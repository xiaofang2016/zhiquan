package com.zhiquan.sport.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
public class ProdApiController {

    private final ProdApiService matches;

    public ProdApiController(ProdApiService matches) {
        this.matches = matches;
    }

    @GetMapping("/prod-api/category/list")
    public KzbResult categories() {
        return KzbResult.ok(matches.categories());
    }

    @GetMapping("/prod-api/match/date/count")
    public KzbResult dates() {
        return KzbResult.ok(matches.dates());
    }

    @GetMapping("/prod-api/match/list/new")
    public KzbResult matchList(@RequestParam(required = false) String starttime,
                               @RequestParam(required = false) Integer type,
                               @RequestParam(required = false) Integer cid,
                               @RequestParam(required = false) Integer ishot,
                               @RequestParam(required = false) Integer pn,
                               @RequestParam(required = false) Integer ps,
                               @RequestParam(name = "live_type", required = false) Integer liveType,
                               @RequestParam(required = false) String since) {
        if (starttime == null || starttime.isBlank()) {
            throw new IllegalArgumentException("缺少必传请求参数");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(starttime);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("starttime 格式应为 yyyy-MM-dd");
        }
        int page = pn == null || pn < 1 ? 1 : Math.min(pn, 500);
        int size = ps == null ? 20 : ps;
        if (size < 1 || size > 200) {
            throw new IllegalArgumentException("ps 必须在 1 到 200 之间");
        }
        return KzbResult.ok(matches.matchList(date, type, cid, ishot, page, size, liveType == null ? 0 : liveType, since));
    }
}
