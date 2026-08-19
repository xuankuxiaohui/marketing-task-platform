package com.mkt.kernel;

/** Appendix C page request: {@code page} ≥ 1, {@code pageSize} default 20, cap 100. */
public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageQuery {
        if (page < 1) {
            page = DEFAULT_PAGE;
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
    }

    public static PageQuery of(Integer page, Integer pageSize) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return new PageQuery(resolvedPage, resolvedSize);
    }

    public long offset() {
        return (long) (page - 1) * (long) pageSize;
    }
}
