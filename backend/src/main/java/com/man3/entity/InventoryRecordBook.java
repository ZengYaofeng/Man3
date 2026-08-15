package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入库盘点批次-漫画明细表
 */
@Data
@TableName("inventory_record_book")
public class InventoryRecordBook {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联盘点批次 inventory_record.id */
    private Long recordId;

    /** 漫画主表ID */
    private Long bookId;

    /** 来源站点漫画ID */
    private String sourceBookId;

    /** 漫画名称(快照) */
    private String bookName;

    /** 该漫画章节总数 */
    private Integer chapterCount;

    /** 该漫画图片总数(入库图片张数) */
    private Integer imageCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
