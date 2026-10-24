package com.zhiquan.sport.worker;

public final class MatchListPages {

    private MatchListPages() {
    }

    public static int laterPageCount(int firstTotalPage, int secondTotalPage) {
        return Math.max(firstTotalPage, secondTotalPage);
    }

    public static boolean pageSizeIgnored(int total, int totalPage, int dataSize, int requestedPs) {
        return requestedPs > 1 && total > 1 && totalPage == total && dataSize <= 1;
    }

    public static int pageCount(int total, int totalPage, int dataSize, int requestedPs, int maxPages) {
        int pages = Math.max(totalPage, dataSize > 0 ? 1 : 0);
        if (dataSize > 0 && dataSize < requestedPs && total > dataSize) {
            int implied = (total + dataSize - 1) / dataSize;
            if (implied > pages) {
                pages = implied;
            }
        }
        if (maxPages > 0) {
            pages = Math.min(pages, maxPages);
        }
        return pages;
    }
}
