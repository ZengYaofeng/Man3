package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class ExternalComicPage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer pageNo;
    private String imgUrl;
    private Long fileSize;
    private Integer imgWidth;
    private Integer imgHeight;
    private String localPath;
    private Integer downloadStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
