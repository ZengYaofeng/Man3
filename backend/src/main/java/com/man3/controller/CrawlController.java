package com.man3.controller;

import com.man3.service.BookPageService;
import com.man3.service.BookService;
import com.man3.service.ChapterService;
import com.man3.utils.crawler.ikanmh.IkanmhConstants;
import com.man3.utils.crawler.ikanmh.IkanmhDetailCrawler;
import com.man3.utils.crawler.ikanmh.IkanmhImageCrawler;
import com.man3.utils.crawler.ikanmh.IkanmhListCrawler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 爬虫触发接口
 */
@Slf4j
@RestController
@RequestMapping("/crawl")
public class CrawlController {

    private final IkanmhListCrawler listCrawler;
    private final IkanmhDetailCrawler detailCrawler;
    private final IkanmhImageCrawler imageCrawler;
    private final BookService bookService;
    private final ChapterService chapterService;
    private final BookPageService bookPageService;

    public CrawlController(IkanmhListCrawler listCrawler, IkanmhDetailCrawler detailCrawler,
                           IkanmhImageCrawler imageCrawler, BookService bookService,
                           ChapterService chapterService, BookPageService bookPageService) {
        this.listCrawler = listCrawler;
        this.detailCrawler = detailCrawler;
        this.imageCrawler = imageCrawler;
        this.bookService = bookService;
        this.chapterService = chapterService;
        this.bookPageService = bookPageService;
    }

    /** 统计: 当前主表/章节/图片数据量 + 详情爬虫进度 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> map = new HashMap<>();
        long total = bookService.countAll();
        long notCrawled = bookService.countByCrawlStatus(IkanmhConstants.STATUS_NOT_CRAWLED);
        long chapterDone = bookService.countByCrawlStatus(IkanmhConstants.STATUS_CHAPTER_DONE);
        long failed = bookService.countByCrawlStatus(IkanmhConstants.STATUS_FAILED);
        map.put("bookCount", total);
        map.put("chapterCount", chapterService.countAll());
        map.put("bookPageCount", bookPageService.countAll());
        // 详情爬虫进度
        map.put("detail", new HashMap<String, Object>() {{
            put("running", IkanmhDetailCrawler.running);
            put("total", total);
            put("notCrawled", notCrawled);
            put("chapterDone", chapterDone);
            put("failed", failed);
            put("done", chapterDone); // 已补全章节=已完成详情
            put("progress", total == 0 ? 0 : (chapterDone * 100 / total));
            put("lastCrawlTime", bookService.maxCrawlTime());
        }});
        return map;
    }

    /** 第一步: 爬全部列表页填充主表(异步执行, 立即返回) */
    @GetMapping("/booklist")
    public Map<String, Object> crawlBooklist() {
        log.info("收到列表爬取任务");
        CompletableFuture.runAsync(listCrawler::crawlAllBooks);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "列表爬取任务已提交, 后台异步执行中");
        return map;
    }

    /** 第二步: 爬主表未爬/失败详情漫画, 补全主表并抓章节填充子表(异步执行, 立即返回) */
    @GetMapping("/detail")
    public Map<String, Object> crawlDetail(Integer limit) {
        log.info("收到详情爬取任务, limit={}", limit);
        final int lim = limit == null ? -1 : limit;
        CompletableFuture.runAsync(() -> detailCrawler.crawlAllDetails(lim));
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "详情爬取任务已提交, 后台异步执行中(断点续爬)");
        return map;
    }

    /** 第三步: 独立爬取章节图片地址填充孙表(异步执行, 立即返回) */
    @GetMapping("/image")
    public Map<String, Object> crawlImage(Integer limit) {
        log.info("收到图片爬取任务, limit={}", limit);
        final int lim = limit == null ? -1 : limit;
        CompletableFuture.runAsync(() -> imageCrawler.crawlAllImages(lim));
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "图片爬取任务已提交, 后台异步执行中");
        return map;
    }

    /** 一/二步连跑(异步执行, 立即返回) */
    @GetMapping("/all")
    public Map<String, Object> crawlAll() {
        log.info("收到全量爬取任务");
        CompletableFuture.runAsync(() -> {
            listCrawler.crawlAllBooks();
            detailCrawler.crawlAllDetails();
        });
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "全量爬取任务已提交, 后台异步执行中");
        return map;
    }
}
