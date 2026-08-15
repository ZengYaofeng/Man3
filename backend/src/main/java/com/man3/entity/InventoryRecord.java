package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入库盘点批次记录表
 */
@Data
@TableName("inventory_record")
public class InventoryRecord {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号(如 INV202608151030001) */
    private String batchNo;

    /** 盘点开始时间 */
    private LocalDateTime startTime;

    /** 盘点结束时间 */
    private LocalDateTime endTime;

    /** 状态: 1-进行中 2-已完成 3-失败 */
    private Integer status;

    /** 扫描的漫画主表数量 */
    private Integer scannedBookCount;

    /** 本次新判定为入库完成的漫画数量 */
    private Integer doneBookCount;

    /** 本次更新入库字段的章节数量 */
    private Integer updatedChapterCount;

    /** 本次更新入库字段的漫画数量 */
    private Integer updatedBookCount;

    /** 备注/错误信息 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
