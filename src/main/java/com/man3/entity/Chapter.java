package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节子表
 */
@Data
@TableName("chapter")
public class Chapter {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属漫画ID */
    private Long bookId;

    /** 章节序号(从1开始, 用于排序) */
    private Integer chapterNo;

    /** 章节标题 */
    private String title;

    /** 来源站点章节ID(如57217) */
    private String sourceChapterId;

    /** 章节阅读页URL */
    private String chapterUrl;

    /** 本章节图片数量 */
    private Integer imageCount;

    /** 爬取状态: 0-未爬取 1-图片已爬取 2-失败 */
    private Integer crawlStatus;

    /** 最近爬取时间 */
    private LocalDateTime crawlTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
