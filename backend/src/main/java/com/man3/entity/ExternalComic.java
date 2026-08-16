package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/** 外部漫画站点的漫画元数据基类，不关联本地章节和图片表。 */
@Data
public abstract class ExternalComic {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceBookId;
    private String name;
    /** Canonical pinyin key used for cross-script comic name matching. */
    private String namePinyin;
    private String author;
    private String tags;
    private String description;
    private String coverUrl;
    private String sourceUrl;
    private Integer chapterCount;
    private String status;
    private String sourceUpdateText;
    /** 1: 本地漫画库已有同名漫画；0: 本地没有。 */
    private Integer isSame;
    private Long matchedBookId;
    private LocalDateTime crawlTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Runtime-only ingestion progress, populated on source-list queries. */
    @TableField(exist = false)
    private Integer ingestedChapterCount;
    @TableField(exist = false)
    private Integer imageDoneChapterCount;
    @TableField(exist = false)
    private Integer pendingChapterCount;
    @TableField(exist = false)
    private Long ingestedImageCount;
    /** 0: not ingested, 1: in progress, 2: complete, 3: local duplicate/no ingest needed. */
    @TableField(exist = false)
    private Integer ingestStatus;
}
