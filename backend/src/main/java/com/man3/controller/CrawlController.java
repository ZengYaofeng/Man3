package com.man3.controller;

import com.man3.service.BookPageService;
import com.man3.service.BookService;
import com.man3.service.ChapterService;
import com.man3.utils.crawler.ikanmh.IkanmhDetailCrawler;
import com.man3.utils.crawler.ikanmh.IkanmhImageCrawler;
import com.man3.utils.crawler.ikanmh.IkanmhListCrawler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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

    /** 统计: 当前主表/章节/图片数据量 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> map = new HashMap<>();
        map.put("bookCount", bookService.countAll());
        map.put("chapterCount", chapterService.countAll());
        map.put("bookPageCount", bookPageService.countAll());
        return map;
    }

    /** 第一步: 爬全部列表页填充主表 */
    @GetMapping("/booklist")
    public Map<String, Object> crawlBooklist() {
        log.info("收到列表爬取任务");
        int count = listCrawler.crawlAllBooks();
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "列表爬取任务已提交, 处理条数: " + count);
        return map;
    }

    /** 第二步: 爬主表未爬详情漫画, 补全主表并抓章节填充子表(limit>0 表示只处理前N部, 便于小批量验证) */
    @GetMapping("/detail")
    public Map<String, Object> crawlDetail(Integer limit) {
        log.info("收到详情爬取任务, limit={}", limit);
        int count = detailCrawler.crawlAllDetails(limit == null ? -1 : limit);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "详情爬取任务已提交, 成功条数: " + count);
        return map;
    }

    /** 第三步: 独立爬取章节图片地址填充孙表(limit>0 表示只处理前N章, 便于小批量验证) */
    @GetMapping("/image")
    public Map<String, Object> crawlImage(Integer limit) {
        log.info("收到图片爬取任务, limit={}", limit);
        int count = imageCrawler.crawlAllImages(limit == null ? -1 : limit);
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "图片爬取任务已提交, 成功章节数: " + count);
        return map;
    }

    /** 一/二步连跑 */
    @GetMapping("/all")
    public Map<String, Object> crawlAll() {
        log.info("收到全量爬取任务");
        int listCount = listCrawler.crawlAllBooks();
        int detailCount = detailCrawler.crawlAllDetails();
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "全量爬取任务已提交, 列表处理: " + listCount + ", 详情成功: " + detailCount);
        return map;
    }
}
