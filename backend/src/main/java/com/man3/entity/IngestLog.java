package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单本漫画图片 URL 入库的审计记录。 */
@Data
@TableName("ingest_log")
public class IngestLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;
    private Long bookId;
    private String bookName;
    /** 1-进行中, 2-完成, 3-失败 */
    private Integer status;
    private Integer crawlStatusBefore;
    private Integer crawlStatusAfter;
    private Integer totalChapterCount;
    private Integer pendingChapterCount;
    private Integer successChapterCount;
    private Integer failedChapterCount;
    private Long imageCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
