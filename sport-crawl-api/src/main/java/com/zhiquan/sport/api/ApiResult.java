package com.zhiquan.sport.api;

public record ApiResult(String code, String msg, Object data) {

    public static ApiResult ok(Object data) {
        return new ApiResult("0", "ok", data);
    }

    public static ApiResult fail(String code, String msg) {
        return new ApiResult(code, msg, null);
    }
}
