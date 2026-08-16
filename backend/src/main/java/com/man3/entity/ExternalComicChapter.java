package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class ExternalComicChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long externalComicId;
    private String sourceChapterId;
    private Integer chapterNo;
    private String title;
    private String chapterUrl;
    private Integer imageCount;
    /** 0 pending, 1 complete, -1 failed, 5 processing. */
    private Integer crawlStatus;
    private LocalDateTime crawlTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
