package com.zhiquan.sport.db;

final class UpstreamFailure {

    private UpstreamFailure() {
    }

    /** 缺参数、路径不存在、原站判定这条查询无效，再请求结果一样。其余失败过几分钟再试。 */
    static boolean giveUp(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("缺少必传请求参数")
                || message.startsWith("HTTP 400")
                || message.startsWith("HTTP 404")
                || message.startsWith("400 ")
                || message.startsWith("404 ")
                || message.startsWith("upstream -1 ");
    }
}
