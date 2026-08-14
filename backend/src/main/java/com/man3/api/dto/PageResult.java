package com.man3.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 通用分页响应
 */
@Data
public class PageResult<T> {

    /** 当前页码 */
    private long page;

    /** 每页条数 */
    private long pageSize;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long totalPages;

    /** 数据列表 */
    private List<T> list;

    public static <T> PageResult<T> of(long page, long pageSize, long total, List<T> list) {
        PageResult<T> r = new PageResult<>();
        r.setPage(page);
        r.setPageSize(pageSize);
        r.setTotal(total);
        r.setTotalPages(pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize);
        r.setList(list);
        return r;
    }
}
