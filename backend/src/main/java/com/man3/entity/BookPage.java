package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 章节图片链接(孙表)
 */
@Data
@TableName("book_page")
public class BookPage {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属章节ID */
    private Long chapterId;

    /** 图片序号(从1开始, 阅读顺序) */
    private Integer pageNo;

    /** 图片URL */
    private String imgUrl;

    /** 本地下载保存路径(可选) */
    private String localPath;

    /** 下载状态: 0-未下载 1-已下载 2-失败 */
    private Integer downloadStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
