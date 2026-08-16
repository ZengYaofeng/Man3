package com.man3.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_stat")
public class SystemStat {
    @TableId
    private Long id;
    private Long bookCount;
    private Long chapterCount;
    private Long totalImageCount;
    private Long downloadedImageCount;
    private Long doneBookCount;
    private Long failedBookCount;
    private LocalDateTime lastReconciledAt;
    private LocalDateTime updatedAt;
}
