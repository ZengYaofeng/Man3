package com.man3.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.entity.Chapter;
import com.man3.service.ChapterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 章节查询接口(供前端章节列表分页展示)
 */
@RestController
@RequestMapping("/api/chapter")
public class ChapterController {

    private final ChapterService chapterService;

    public ChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    /**
     * 分页查询某漫画的章节
     *
     * @param bookId   漫画ID(必填)
     * @param page     页码(从1开始, 默认1)
     * @param pageSize 每页条数(默认20)
     * @param orderDir 排序方向 asc(正序,默认)/desc(倒序)
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam Long bookId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "asc") String orderDir) {

        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        IPage<Chapter> result = chapterService.pageByBookId(bookId, page, pageSize, orderDir);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());

        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "success");
        map.put("data", data);
        return map;
    }
}
