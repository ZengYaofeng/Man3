package com.man3.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.api.dto.PageResult;
import com.man3.api.dto.SiteSourceSaveDTO;
import com.man3.entity.SiteSource;
import com.man3.service.SiteSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 站点来源接口(供前端调用)
 */
@Slf4j
@RestController
@RequestMapping("/api/site")
@CrossOrigin(origins = "*")   // 允许前端跨域访问
public class SiteApiController {

    private final SiteSourceService siteSourceService;

    public SiteApiController(SiteSourceService siteSourceService) {
        this.siteSourceService = siteSourceService;
    }

    /**
     * 分页 + 关键词查询站点列表
     * GET /api/site/list?page=1&pageSize=20&keyword=ikan
     */
    @GetMapping("/list")
    public Map<String, Object> list(@ModelAttribute SiteQuery query) {
        int page = query.getPage() == null ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        IPage<SiteSource> result = siteSourceService.pageQuery(page, pageSize, query.getKeyword());

        PageResult<SiteSource> pageResult = PageResult.of(
                result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("data", pageResult);
        return resp;
    }

    /**
     * 全量站点(供全站查重页面下拉框)
     * GET /api/site/all
     */
    @GetMapping("/all")
    public Map<String, Object> all() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("data", siteSourceService.listAll());
        return resp;
    }

    /**
     * 新增或更新站点
     * POST /api/site/save
     */
    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody SiteSourceSaveDTO dto) {
        Map<String, Object> resp = new HashMap<>();
        if (dto.getName() == null || dto.getName().trim().isEmpty()
                || dto.getUrl() == null || dto.getUrl().trim().isEmpty()) {
            resp.put("code", 400);
            resp.put("message", "网站名称与网址为必填项");
            return resp;
        }
        try {
            SiteSource saved = siteSourceService.saveOrUpdate(dto);
            resp.put("code", 0);
            resp.put("message", "success");
            resp.put("data", saved);
        } catch (IllegalArgumentException e) {
            resp.put("code", 404);
            resp.put("message", e.getMessage());
        }
        return resp;
    }

    /**
     * 切换启用状态
     * POST /api/site/{id}/enabled?enabled=true
     */
    @PostMapping("/{id}/enabled")
    public Map<String, Object> setEnabled(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam boolean enabled) {
        siteSourceService.setEnabled(id, enabled);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        return resp;
    }

    /**
     * 删除站点
     * DELETE /api/site/{id}
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@org.springframework.web.bind.annotation.PathVariable Long id) {
        siteSourceService.delete(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        return resp;
    }

    /** 列表查询入参 */
    @lombok.Data
    public static class SiteQuery {
        private Integer page;
        private Integer pageSize;
        private String keyword;
    }
}
