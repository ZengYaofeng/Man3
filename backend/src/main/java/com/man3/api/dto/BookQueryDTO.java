package com.man3.api.dto;

import lombok.Data;

/**
 * 漫画列表查询条件
 */
@Data
public class BookQueryDTO {

    /** 页码(从1开始) */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 关键词(匹配名称/别名/作者) */
    private String keyword;

    /** 地区筛选(如 日本/韩国/国产) */
    private String region;

    /** 状态筛选(连载中/已完结) */
    private String status;

    /** 标签筛选(模糊匹配) */
    private String tag;

    /** 爬取状态筛选(0=未爬, 1=章节完成, 2=图片完成, 3=完成) */
    private Integer crawlStatus;

    /** 排序字段: score(评分) | updateTime(更新时间) | clicks(点击量) | createdAt(入库时间) */
    private String sortField = "createdAt";

    /** 排序方向: desc(降序) | asc(升序) */
    private String sortDir = "desc";
}
