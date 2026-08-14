package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 漫画主表
 */
@Data
@TableName("book")
public class Book {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源站点漫画ID(如1224), 用于去重 */
    private String sourceBookId;

    /** 漫画名称 */
    private String name;

    /** 漫画别名 */
    private String alias;

    /** 作者(多个用&或,分隔) */
    private String author;

    /** 连载状态(连载中/已完结) */
    private String status;

    /** 地区/国家 */
    private String region;

    /** 标签(多个用,分隔) */
    private String tags;

    /** 漫画简介 */
    private String description;

    /** 封面图片链接 */
    private String coverUrl;

    /** 站点显示的更新时间 */
    private LocalDateTime updateTime;

    /** 点击量 */
    private Long clicks;

    /** 评分 */
    private BigDecimal score;

    /** 漫画主页URL */
    private String sourceUrl;

    /** 爬取状态: 0-未爬取 1-章节已爬取 2-图片已爬取 3-全部完成 -1-失败 */
    private Integer crawlStatus;

    /** 最近爬取时间 */
    private LocalDateTime crawlTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
