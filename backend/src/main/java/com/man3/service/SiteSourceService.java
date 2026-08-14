package com.man3.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.entity.SiteSource;

import java.util.List;

/**
 * 漫画来源站点服务
 */
public interface SiteSourceService {

    /**
     * 分页 + 关键词查询站点列表
     */
    IPage<SiteSource> pageQuery(int page, int pageSize, String keyword);

    /**
     * 全量查询(用于全站查重页面的站点下拉框)
     */
    List<SiteSource> listAll();

    /**
     * 根据ID查询
     */
    SiteSource getById(Long id);

    /**
     * 新增或更新(有ID则更新, 无ID则新增)
     */
    SiteSource saveOrUpdate(com.man3.api.dto.SiteSourceSaveDTO dto);

    /**
     * 切换启用状态
     */
    void setEnabled(Long id, boolean enabled);

    /**
     * 删除站点
     */
    void delete(Long id);
}
