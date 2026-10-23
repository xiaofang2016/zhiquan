package com.zhiquan.sport.api;

public record KzbResult(String msg, String code, Object data) {

    public static KzbResult ok(Object data) {
        return new KzbResult("ok", "0", data);
    }
}
