package com.man3.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.api.dto.BookQueryDTO;
import com.man3.api.dto.PageResult;
import com.man3.entity.Book;
import com.man3.service.BookService;
import com.man3.service.SystemStatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 漫画数据查询接口(供前端调用)
 */
@Slf4j
@RestController
@RequestMapping("/api/book")
@CrossOrigin(origins = "*")   // 允许前端跨域访问
public class BookApiController {

    private final BookService bookService;
    private final SystemStatService systemStatService;

    public BookApiController(BookService bookService, SystemStatService systemStatService) {
        this.bookService = bookService;
        this.systemStatService = systemStatService;
    }

    /**
     * 分页 + 多条件查询漫画列表
     * 示例:
     *   GET /api/book/list?page=1&pageSize=20
     *   GET /api/book/list?keyword=海贼&region=日本&sortField=score&sortDir=desc
     */
    @GetMapping("/list")
    public Map<String, Object> list(@ModelAttribute BookQueryDTO query) {
        try {
            int page = query.getPage() == null ? 1 : query.getPage();
            int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
            IPage<Book> result = bookService.pageQuery(
                    page, pageSize,
                    query.getKeyword(), query.getRegion(), query.getStatus(),
                    query.getTag(),                     query.getSortField(), query.getSortDir(),
                    query.getIngestStatus(), query.getChapterMin(), query.getChapterMax());

            PageResult<Book> pageResult = PageResult.of(
                    result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());

            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 0);
            resp.put("message", "success");
            resp.put("data", pageResult);
            return resp;
        } catch (Exception e) {
            log.error("/api/book/list error", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("code", 500);
            resp.put("message", e.getMessage());
            return resp;
        }
    }

    /**
     * 漫画详情(含来源ID, 前端可拼接阅读页)
     * GET /api/book/{id}
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Book book = bookService.getById(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", book == null ? 404 : 0);
        resp.put("message", book == null ? "not found" : "success");
        resp.put("data", book);
        return resp;
    }

    /**
     * 筛选维度枚举(供前端下拉框)
     * GET /api/book/options
     */
    @GetMapping("/options")
    public Map<String, Object> options() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        Map<String, Object> data = new HashMap<>();
        data.put("regions", new String[]{"日本", "韩国", "国产", "欧美", "其他"});
        data.put("statuses", new String[]{"连载中", "已完结"});
        data.put("sortFields", new String[]{"score", "updateTime", "clicks", "createdAt"});
        resp.put("data", data);
        return resp;
    }

    /**
     * 仪表盘统计概览(真实数据)
     * GET /api/book/stats
     * 返回: 漫画总数 / 章节总数 / 图片总数 / 已下载图片数 / 爬取完成数 / 爬取失败数
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> data = new HashMap<>();
        com.man3.entity.SystemStat stat = systemStatService.snapshot();
        data.put("bookCount", stat.getBookCount());
        data.put("chapterCount", stat.getChapterCount());
        data.put("totalImageCount", stat.getTotalImageCount());
        data.put("downloadedImageCount", stat.getDownloadedImageCount());

        // crawl_status: 3-全部完成 -1-失败
        data.put("doneCount", stat.getDoneBookCount());
        data.put("failedCount", stat.getFailedBookCount());

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        resp.put("message", "success");
        resp.put("data", data);
        return resp;
    }
}
