package com.zhiquan.sport.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DomainRewriteController {

    private final DomainRewrites rewrites;

    public DomainRewriteController(DomainRewrites rewrites) {
        this.rewrites = rewrites;
    }

    @GetMapping("/prod-api/domain-rewrites")
    public ResponseEntity<KzbResult> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(KzbResult.ok(rewrites.rows()));
    }
}
