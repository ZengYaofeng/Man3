package com.man3.controller;

import com.man3.mapper.BookPageMapper;
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
@RequestMapping("/api/crawl")
public class CrawlController {

    private final IkanmhListCrawler listCrawler;
    private final IkanmhDetailCrawler detailCrawler;
    private final IkanmhImageCrawler imageCrawler;
    private final BookService bookService;
    private final ChapterService chapterService;
    private final BookPageService bookPageService;

    public CrawlController(IkanmhListCrawler listCrawler, IkanmhDetailCrawler detailCrawler,
                           IkanmhImageCrawler imageCrawler,
                           BookService bookService, ChapterService chapterService,
                           BookPageService bookPageService) {
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
        // 图片爬虫进度
        long imgPending = chapterService.countByImageStatus(IkanmhConstants.STATUS_NOT_CRAWLED);
        long imgDone = chapterService.countByImageStatus(IkanmhConstants.STATUS_IMAGE_DONE);
        long imgFailed = chapterService.countByImageStatus(IkanmhConstants.STATUS_FAILED);
        long imgProcessing = chapterService.countByImageStatus(IkanmhConstants.STATUS_IMAGE_PROCESSING);
        long imgTotalCh = chapterService.countAll();
        long imgProcessed = imgDone + imgFailed;
        // 章节维度进度(已处理章节/总章节)
        double chProgress = imgTotalCh == 0 ? 0 : (double) imgProcessed * 100.0 / imgTotalCh;
        double imgSuccessRate = imgProcessed == 0 ? 0 : (double) imgDone * 100.0 / imgProcessed;
        long bookPageCount = bookPageService.countAll();
        // 图片维度进度: 已入库图片 / 估算图片总数
        // 估算图片总数 = 已爬章节平均每章图片数 × 总章节数
        long sumImgCrawled = chapterService.sumImageCountCrawled();
        double avgImgPerChapter = (imgProcessed > 0) ? (double) sumImgCrawled / imgProcessed : 0;
        double imgTotalEstimate = avgImgPerChapter * imgTotalCh;
        double imgProgress = (imgTotalEstimate > 0) ? (double) bookPageCount * 100.0 / imgTotalEstimate : 0;

        map.put("image", new HashMap<String, Object>() {{
            put("running", IkanmhImageCrawler.running);
            put("pending", imgPending);   // 待爬章节
            put("done", imgDone);         // 图片已爬章节
            put("failed", imgFailed);     // 失败章节
            put("processing", imgProcessing); // 处理中章节(已领取未完)
            put("totalCh", imgTotalCh);   // 章节总数
            put("processed", imgProcessed); // 已处理章节
            put("progress", Math.round(chProgress * 100.0) / 100.0);   // 章节进度(%)
            put("successRate", Math.round(imgSuccessRate * 100.0) / 100.0);
            put("bookPageCount", bookPageCount); // 已入库图片总数
            put("imgProgress", Math.round(imgProgress * 100.0) / 100.0); // 图片进度(%)
        }});
        return map;
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
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
        CompletableFuture.runAsync(() -> imageCrawler.crawlAllImages());
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "图片爬取任务已提交, 后台异步执行中");
        return map;
    }

    /** 启动图片爬虫(全量, 断点续爬), 与 /image?limit=-1 等价, 供前端"开始"按钮调用 */
    @GetMapping("/image/start")
    public Map<String, Object> startImage() {
        if (IkanmhImageCrawler.running) {
            Map<String, Object> map = new HashMap<>();
            map.put("code", 1);
            map.put("message", "图片爬虫已在运行中");
            return map;
        }
        log.info("收到启动图片爬取任务(全量)");
        CompletableFuture.runAsync(() -> imageCrawler.crawlAllImages());
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "图片爬取任务已提交, 后台异步执行中(断点续爬)");
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

    /** 停止当前正在运行的爬虫(当前批次处理完即退出, 不强制中断) */
    @GetMapping("/stop")
    public Map<String, Object> stop() {
        log.info("收到停止爬虫指令");
        IkanmhDetailCrawler.requestStop();
        IkanmhListCrawler.requestStop();
        IkanmhImageCrawler.requestStop();
        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "已发送停止指令, 爬虫将在当前批次结束后停止");
        return map;
    }
}
