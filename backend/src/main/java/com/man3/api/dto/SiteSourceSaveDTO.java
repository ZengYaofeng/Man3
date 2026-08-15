package com.man3.api.dto;

import lombok.Data;

/**
 * 站点来源保存/更新入参
 */
@Data
public class SiteSourceSaveDTO {

    /** 主键ID(更新时必填, 新增时忽略) */
    private Long id;

    /** 网站名称 */
    private String name;

    /** 网址(站点根域名) */
    private String url;

    /** 图片规则 */
    private String imageRule;

    /** 漫画详情网址模板 */
    private String detailUrl;

    /** 漫画内容网址模板 */
    private String contentUrl;

    /** 是否启用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}
