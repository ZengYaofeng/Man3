package com.man3.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漫画来源站点表
 */
@Data
@TableName("site_source")
public class SiteSource {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 网站名称 */
    private String name;

    /** 网址(站点根域名) */
    private String url;

    /** 图片规则(图片URL解析/替换规则说明) */
    private String imageRule;

    /** 漫画详情网址模板(支持{bookId}/{id}占位符) */
    private String detailUrl;

    /** 漫画内容(阅读页)网址模板(支持{chapterId}/{id}占位符) */
    private String contentUrl;

    /** 是否启用(1启用 0停用) */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
