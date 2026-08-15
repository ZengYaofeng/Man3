package com.man3.utils.crawler.ikanmh;

import com.man3.config.IkanmhProperties;
import com.man3.entity.Book;
import com.man3.entity.Chapter;
import com.man3.mapper.BookMapper;
import com.man3.mapper.ChapterMapper;
import com.man3.service.BookPageService;
import com.man3.service.ChapterService;
import com.man3.utils.CrawlerUtils;
import com.man3.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * ikanmh 图片链接爬虫
 *
 * 职责: 遍历章节, 从章节阅读页解析图片URL并入库到 book_page(不下载图片字节, 仅取头部解析尺寸)。
 *
 * 并发架构(生产者-消费者):
 *  - 单线程生产者: 分批拉取 crawl_status=0 的章节, 立即标记为"处理中"(5) 防止重复领取
 *  - 多线程消费者: 由线程池(大小=maxConcurrentDownloads)并发处理各章节
 *  - 章内图片并发: 每章内部图片用 downloadThreadPoolSize 线程池并发解析URL+尺寸
 *  - 全局信号量: 限制同时向源站发起的请求数, 避免打爆/封禁
 *  - 全程不下载完整图片, 仅用 Range 请求取图片头解析尺寸, 单章耗时大幅降低
 */
@Slf4j
@Component
public class IkanmhImageCrawler {

    /** 并发工作时, 单章内图片解析的 Range 请求最大字节数(足够解析尺寸, 不下载整图) */
    private static final int IMAGE_HEAD_BYTES = 65536;

    /** 单次从 DB 拉取的待处理章节批次大小 */
    private static final int FETCH_BATCH = 300;

    /** 生产者等待线程池空闲的间隔 */
    private static final long PRODUCER_POLL_MS = 200;

    public static volatile boolean running = false;
    public static volatile boolean stopFlag = false;
    /** Atomically reserves the shared crawler instance for one run. */
    private static final AtomicBoolean RUN_GUARD = new AtomicBoolean(false);

    private final IkanmhProperties props;
    private final HttpClientUtils http;
    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;
    private final BookPageService bookPageService;
    private final BookMapper bookMapper;

    /** 并发章节处理线程池(每章仅 1 次章节页请求, 不逐图请求源站) */
    private ExecutorService chapterPool;

    /** 统计: 成功/失败章节数 */
    private final AtomicLong doneCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);

    /** 正在处理中(已领取未完成的)的章节ID, 用于避免同一章被重复处理 */
    private final Map<Long, Boolean> claimed = new ConcurrentHashMap<>();

    /** 内存爬虫日志: bookId -> 日志条目(控制台风格, 便于前端实时查看) */
    private static final Map<Long, List<CrawlLogEntry>> LOG_BUFFER = new ConcurrentHashMap<>();
    /** 每个 bookId 的实时计数(已处理章节/成功/失败/总章节) */
    private static final Map<Long, CrawlProgressSnapshot> PROGRESS_MAP = new ConcurrentHashMap<>();
    private static final int MAX_LOG_PER_BOOK = 500;

    /** 全量爬虫的虚拟 bookId */
    public static final long GLOBAL_BOOK_ID = -1L;

    /** 日志条目 */
    public static class CrawlLogEntry {
        public final long ts;
        public final String level;
        public final String msg;
        public CrawlLogEntry(long ts, String level, String msg) {
            this.ts = ts;
            this.level = level;
            this.msg = msg;
        }
    }

    /** 进度快照 */
    public static class CrawlProgressSnapshot {
        public volatile long total = 0;
        public volatile long processed = 0;
        public volatile long success = 0;
        public volatile long fail = 0;
        public volatile boolean running = false;
        public volatile String currentChapter = "";
        public volatile long currentImageCount = 0;
    }

    /** 追加一条日志(按 bookId 维度) */
    private static void appendLog(long bookId, String level, String msg) {
        LOG_BUFFER.computeIfAbsent(bookId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new CrawlLogEntry(System.currentTimeMillis(), level, msg));
        List<CrawlLogEntry> list = LOG_BUFFER.get(bookId);
        if (list.size() > MAX_LOG_PER_BOOK) {
            list.subList(0, list.size() - MAX_LOG_PER_BOOK).clear();
        }
    }

    /** 供控制器查询: 返回某 bookId 的最近日志 */
    public List<CrawlLogEntry> getLogs(long bookId) {
        List<CrawlLogEntry> list = LOG_BUFFER.get(bookId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    /** 供控制器查询: 返回某 bookId 的实时进度快照 */
    public CrawlProgressSnapshot getProgress(long bookId) {
        return PROGRESS_MAP.computeIfAbsent(bookId, k -> new CrawlProgressSnapshot());
    }

    /** 清空某 bookId 的日志与进度(新一轮入库前调用) */
    private static void resetForBook(long bookId) {
        LOG_BUFFER.remove(bookId);
        CrawlProgressSnapshot snap = PROGRESS_MAP.computeIfAbsent(bookId, k -> new CrawlProgressSnapshot());
        snap.total = 0;
        snap.processed = 0;
        snap.success = 0;
        snap.fail = 0;
        snap.currentChapter = "";
        snap.currentImageCount = 0;
    }

    public IkanmhImageCrawler(IkanmhProperties props, HttpClientUtils http,
                              ChapterService chapterService, ChapterMapper chapterMapper,
                              BookPageService bookPageService, BookMapper bookMapper) {
        this.props = props;
        this.http = http;
        this.chapterService = chapterService;
        this.chapterMapper = chapterMapper;
        this.bookPageService = bookPageService;
        this.bookMapper = bookMapper;
    }

    /** 请求外部停止(由控制器调用) */
    public static void requestStop() {
        stopFlag = true;
    }

    private boolean acquireRun() {
        if (!RUN_GUARD.compareAndSet(false, true)) {
            return false;
        }
        running = true;
        return true;
    }

    private void releaseRun() {
        running = false;
        RUN_GUARD.set(false);
    }

    public void crawlAllImages() {
        if (!acquireRun()) {
            log.warn("图片爬虫已在运行中, 忽略重复启动");
            return;
        }
        stopFlag = false;
        doneCount.set(0);
        failCount.set(0);
        claimed.clear();

        // 断点续爬: 将上次中断遗留的"处理中"章节回滚为待爬, 避免永久卡住
        try {
            Chapter rollback = new Chapter();
            rollback.setCrawlStatus(IkanmhConstants.STATUS_NOT_CRAWLED);
            int rolled = chapterMapper.update(rollback,
                    new LambdaQueryWrapper<Chapter>().eq(Chapter::getCrawlStatus, IkanmhConstants.STATUS_IMAGE_PROCESSING));
            if (rolled > 0) {
                log.info("图片爬虫: 回滚 {} 个上次中断的'处理中'章节为待爬", rolled);
            }
        } catch (Exception e) {
            log.warn("回滚处理中章节失败: {}", e.getMessage());
        }

        int chapterConcurrency = Math.max(1, props.getMaxConcurrentDownloads());
        chapterPool = Executors.newFixedThreadPool(chapterConcurrency,
                r -> {
                    Thread t = new Thread(r, "img-chapter-" + tSeq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });

        log.info("图片爬虫启动: 章节并发={}(仅抓取章节页解析图片URL, 不下载图片/不逐图请求)", chapterConcurrency);

        try {
            while (!stopFlag) {
                // 1) 生产者: 拉取一批未爬章节
                List<Chapter> batch = chapterService.listUncrawledImages(FETCH_BATCH);
                if (batch.isEmpty()) {
                    if (claimed.isEmpty()) {
                        log.info("图片爬虫: 暂无待处理章节, 结束");
                        break;
                    }
                    // 还有章节在处理中, 等待它们完成
                    CrawlerUtils.sleep(PRODUCER_POLL_MS);
                    continue;
                }

                // 2) 标记为处理中, 防止其他实例/轮询重复领取
                List<Long> ids = new ArrayList<>(batch.size());
                for (Chapter c : batch) {
                    ids.add(c.getId());
                    claimed.put(c.getId(), Boolean.TRUE);
                }
                chapterService.markImageProcessing(ids);

                // 3) 提交给并发线程池处理各章节
                for (Chapter ch : batch) {
                    chapterPool.submit(() -> processChapter(ch, GLOBAL_BOOK_ID));
                }

                // 4) 控制生产节奏: 避免一次性把所有章节都标记为处理中占用过多内存,
                //    仅当 claimed(处理中)数量低于一定水位时继续拉取
                while (claimed.size() > FETCH_BATCH * 2 && !stopFlag) {
                    CrawlerUtils.sleep(PRODUCER_POLL_MS);
                }
            }

            // 等待剩余章节处理完成
            log.info("图片爬虫: 等待剩余 {} 个章节处理完成...", claimed.size());
            while (!claimed.isEmpty() && !stopFlag) {
                CrawlerUtils.sleep(PRODUCER_POLL_MS);
            }
        } finally {
            releaseRun();
            if (chapterPool != null) {
                chapterPool.shutdownNow();
            }
            log.info("图片爬虫结束: 成功章节={}, 失败章节={}", doneCount.get(), failCount.get());
        }
    }

    /** 处理单个章节: 解析阅读页图片URL, 并发解析尺寸并入库 */
    private void processChapter(Chapter chapter, long logBookId) {
        CrawlProgressSnapshot snap = PROGRESS_MAP.computeIfAbsent(logBookId, k -> new CrawlProgressSnapshot());
        String title = (chapter.getTitle() != null ? chapter.getTitle() : ("第" + chapter.getChapterNo() + "话"));
        try {
            if (stopFlag) {
                return;
            }
            long t0 = System.currentTimeMillis();
            String url = props.getBaseUrl() + IkanmhConstants.CHAPTER_PATH + chapter.getSourceChapterId();
            appendLog(logBookId, "INFO", "▶ 开始爬取章节: " + title + " [" + url + "]");
            snap.currentChapter = title;
            Document doc = http.get(url);
            if (doc == null) {
                // 源站访问失败(网络/限流), 回滚为待爬以便重试
                log.warn("章节 {} 阅读页获取失败(回滚待爬): {}", chapter.getId(), url);
                appendLog(logBookId, "WARN", "✗ 章节获取失败(网络/限流), 回滚待爬: " + title);
                try {
                    Chapter rollback = new Chapter();
                    rollback.setCrawlStatus(IkanmhConstants.STATUS_NOT_CRAWLED);
                    chapterMapper.update(rollback, new LambdaQueryWrapper<Chapter>()
                            .eq(Chapter::getId, chapter.getId())
                            .eq(Chapter::getCrawlStatus, IkanmhConstants.STATUS_IMAGE_PROCESSING));
                } catch (Exception ex) {
                    log.warn("回滚章节 {} 失败: {}", chapter.getId(), ex.getMessage());
                }
                failCount.incrementAndGet();
                snap.fail++;
                snap.processed++;
                return;
            }

            // 兼容 ikAanmh.top / ikanmh.top: 图片为懒加载, 真实地址在 data-original 属性
            List<Element> imgEls = doc.select("div#cp_img img");
            if (imgEls.isEmpty()) {
                imgEls = doc.select("div.image-comic img");
            }
            if (imgEls.isEmpty()) {
                imgEls = doc.select("img.lazy");
            }
            if (imgEls.isEmpty()) {
                imgEls = doc.select("img[class~=(?i)lazy]");
            }

            List<String> urls = new ArrayList<>();
            for (Element img : imgEls) {
                String src = img.attr("data-original");
                if (!StringUtils.hasText(src)) {
                    src = img.attr("data-src");
                }
                if (!StringUtils.hasText(src)) {
                    src = CrawlerUtils.extractUrlFromStyle(img.attr("style"));
                }
                if (!StringUtils.hasText(src)) {
                    src = img.attr("src");
                }
                if (StringUtils.hasText(src)) {
                    urls.add(src.trim());
                }
            }

            if (urls.isEmpty()) {
                log.warn("章节 {} 未解析到图片(标记失败): {}", chapter.getId(), url);
                appendLog(logBookId, "WARN", "✗ 未解析到图片, 标记失败: " + title);
                chapterService.updateImageResult(chapter.getId(), 0, false);
                failCount.incrementAndGet();
                snap.fail++;
                snap.processed++;
                return;
            }

            // 直接入库图片URL(不下载/不逐图请求源站, 尺寸暂置0, 后续可按需解析)
            int n = urls.size();
            List<Long> zerosLong = new ArrayList<>(Collections.nCopies(n, 0L));
            List<Integer> zerosInt = new ArrayList<>(Collections.nCopies(n, 0));
            bookPageService.syncPages(chapter.getId(), urls, zerosLong, zerosInt, zerosInt);

            long cost = System.currentTimeMillis() - t0;
            doneCount.incrementAndGet();
            log.info("章节图片入库完成 id={} 图片数={} 耗时={}ms", chapter.getId(), n, cost);
            appendLog(logBookId, "INFO", "✓ 章节完成: " + title + " 图片数=" + n + " 耗时=" + cost + "ms");
            chapterService.updateImageResult(chapter.getId(), n, true);
            snap.success++;
            snap.processed++;
            snap.currentImageCount = n;

        } catch (Exception e) {
            // 网络/解析类异常: 源站抖动或临时不可达, 回滚为待爬以便后续重试, 避免污染数据
            log.warn("章节图片处理异常 id={} (回滚待爬): {}", chapter.getId(), e.getMessage());
            appendLog(logBookId, "ERROR", "✗ 章节处理异常: " + title + " " + e.getMessage());
            try {
                Chapter rollback = new Chapter();
                rollback.setCrawlStatus(IkanmhConstants.STATUS_NOT_CRAWLED);
                chapterMapper.update(rollback, new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getId, chapter.getId())
                        .eq(Chapter::getCrawlStatus, IkanmhConstants.STATUS_IMAGE_PROCESSING));
            } catch (Exception ex) {
                log.warn("回滚章节 {} 失败: {}", chapter.getId(), ex.getMessage());
            }
            failCount.incrementAndGet();
            snap.fail++;
            snap.processed++;
        } finally {
            claimed.remove(chapter.getId());
        }
    }

    /**
     * 从图片字节头解析宽高(JPEG/PNG/GIF), 解析失败返回 [0,0]
     */
    public static int[] readImageSize(byte[] data) {
        if (data == null || data.length < 16) {
            return new int[]{0, 0};
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            BufferedImage img = ImageIO.read(bais);
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception ignore) {
            // ImageIO 读取 range 片段可能不完整, 退化为手动解析
        }
        // 手动解析常见格式头部
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
            // JPEG: 遍历 marker 找 SOF
            int p = 2;
            while (p + 9 < data.length) {
                if (data[p] != (byte) 0xFF) {
                    p++;
                    continue;
                }
                int marker = data[p + 1] & 0xFF;
                if ((marker >= 0xC0 && marker <= 0xC3) || (marker >= 0xC5 && marker <= 0xC7)
                        || (marker >= 0xC9 && marker <= 0xCB) || (marker >= 0xCD && marker <= 0xCF)) {
                    int height = ((data[p + 5] & 0xFF) << 8) | (data[p + 6] & 0xFF);
                    int width = ((data[p + 7] & 0xFF) << 8) | (data[p + 8] & 0xFF);
                    return new int[]{width, height};
                }
                int len = ((data[p + 2] & 0xFF) << 8) | (data[p + 3] & 0xFF);
                p += 2 + len;
            }
        } else if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50) {
            // PNG
            int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16)
                    | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16)
                    | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
            return new int[]{width, height};
        } else if (data[0] == (byte) 'G' && data[1] == (byte) 'I' && data[2] == (byte) 'F') {
            // GIF
            int width = ((data[6] & 0xFF)) | ((data[7] & 0xFF) << 8);
            int height = ((data[8] & 0xFF)) | ((data[9] & 0xFF) << 8);
            return new int[]{width, height};
        }
        return new int[]{0, 0};
    }

    private static final AtomicLong tSeq = new AtomicLong(0);

    /**
     * 单本漫画图片入库: 仅爬取指定漫画下 crawl_status=0 的章节。
     * 复用全局 running 标志与并发处理流程(与全量爬虫互斥, 不会同时运行)。
     * 完成后把图片齐全的章节置为已爬(status=1)并刷新主表计数。
     *
     * @param bookId 漫画ID
     */
    public void crawlImagesForBook(Long bookId) {
        if (bookId == null) {
            log.warn("单本图片入库: bookId 为空, 忽略");
            return;
        }
        if (!acquireRun()) {
            log.warn("图片爬虫已在运行中, 忽略重复启动(单本 bookId={})", bookId);
            appendLog(bookId, "WARN", "爬虫已在运行中, 本次入库被忽略");
            return;
        }
        // 重置该漫画的日志与进度快照
        resetForBook(bookId);
        CrawlProgressSnapshot snap = PROGRESS_MAP.computeIfAbsent(bookId, k -> new CrawlProgressSnapshot());
        snap.running = true;

        // 入库进行中, 先把漫画标记为"处理中"(5), 供列表"入库中"筛选; 收尾时由 refreshBookCounters 修正
        try {
            Book flag = new Book();
            flag.setId(bookId);
            flag.setCrawlStatus(IkanmhConstants.STATUS_IMAGE_PROCESSING);
            bookMapper.updateById(flag);
        } catch (Exception ignore) {
        }

        stopFlag = false;
        doneCount.set(0);
        failCount.set(0);
        claimed.clear();

        // 初始化并发章节处理线程池(单本爬虫需要, 不能与全量共享未初始化的引用)
        int chapterConcurrency = Math.max(1, props.getMaxConcurrentDownloads());
        chapterPool = Executors.newFixedThreadPool(chapterConcurrency,
                r -> {
                    Thread t = new Thread(r, "img-book-" + bookId + "-" + tSeq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });

        // 统计该漫画待爬(图片未真实入库)章节总数, 作为进度分母
        // 判定标准: 章节没有 book_page, 或存在 img_url 缺失的图片(不依赖 crawl_status 标记, 避免脏状态导致漏爬)
        int totalPending = chapterService.countPendingImageChapters(bookId);
        snap.total = totalPending;

        log.info("单本图片入库启动: bookId={}, 待爬章节={}", bookId, totalPending);
        appendLog(bookId, "INFO", "▶ 单本图片入库启动: bookId=" + bookId + " 待爬章节=" + totalPending);
        if (totalPending == 0) {
            appendLog(bookId, "WARN",
                    "该漫画待爬章节为 0: 图片已全部入库, 或需先运行【详情爬虫】补全章节. 本次无需爬取.");
        }
        try {
            while (!stopFlag) {
                // Only status=0 chapters may be claimed. The page-based query also returns
                // status=5 chapters, which caused the producer to submit the same chapter repeatedly.
                List<Chapter> candidates = chapterService.listUncrawledImagesForBook(bookId, FETCH_BATCH);
                List<Chapter> batch = new ArrayList<>();
                for (Chapter candidate : candidates) {
                    if (claimed.putIfAbsent(candidate.getId(), Boolean.TRUE) == null) {
                        batch.add(candidate);
                    }
                }
                if (batch.isEmpty()) {
                    if (claimed.isEmpty()) {
                        log.info("单本图片入库: 该漫画待处理章节已清空, 结束 bookId={}", bookId);
                        break;
                    }
                    CrawlerUtils.sleep(PRODUCER_POLL_MS);
                    continue;
                }

                List<Long> ids = new ArrayList<>(batch.size());
                for (Chapter c : batch) {
                    ids.add(c.getId());
                }
                chapterService.markImageProcessing(ids);

                for (Chapter ch : batch) {
                    chapterPool.submit(() -> processChapter(ch, bookId));
                }

                while (claimed.size() > FETCH_BATCH * 2 && !stopFlag) {
                    CrawlerUtils.sleep(PRODUCER_POLL_MS);
                }
            }

            while (!claimed.isEmpty() && !stopFlag) {
                CrawlerUtils.sleep(PRODUCER_POLL_MS);
            }

            // 单本完成: 先把"处理中"残留且未真实入库的章节重置为未爬, 再把图片齐全的章节标记为已爬(status=1), 刷新主表计数
            try {
                log.info("单本图片入库收尾: 标记已爬章节并刷新计数 bookId={}", bookId);
                try {
                    chapterMapper.resetStuckProcessingChapters(java.util.Collections.singletonList(bookId));
                } catch (Exception re) {
                    log.warn("重置卡住的章节状态失败 bookId={}: {}", bookId, re.getMessage());
                }
                chapterMapper.markChaptersImageDone(java.util.Collections.singletonList(bookId));
                refreshBookCounters(bookId);
                appendLog(bookId, "INFO",
                        "✓ 单本入库收尾完成: 标记已爬章节并刷新计数, 成功章节=" + snap.success + " 失败章节=" + snap.fail);
            } catch (Exception e) {
                log.warn("单本图片入库收尾失败 bookId={}: {}", bookId, e.getMessage());
                appendLog(bookId, "ERROR", "收尾失败: " + e.getMessage());
            }
        } finally {
            releaseRun();
            snap.running = false;
            snap.currentChapter = "";
            if (chapterPool != null) {
                chapterPool.shutdownNow();
            }
            log.info("单本图片入库结束: bookId={}, 成功章节={}, 失败章节={}",
                    bookId, doneCount.get(), failCount.get());
            appendLog(bookId, "INFO",
                    "■ 单本图片入库结束: bookId=" + bookId + " 成功章节=" + snap.success + " 失败章节=" + snap.fail);
        }
    }

    /** 刷新主表章节/图片计数(避免循环依赖, 直接走 mapper) */
    private void refreshBookCounters(Long bookId) {
        if (bookId == null) {
            return;
        }
        Long chapterCount = chapterMapper.selectCount(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getBookId, bookId));
        List<Long> chapterIds = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .select(Chapter::getId)
                        .eq(Chapter::getBookId, bookId))
                .stream().map(Chapter::getId).collect(java.util.stream.Collectors.toList());
        Long imageCount = 0L;
        if (!chapterIds.isEmpty()) {
            imageCount = bookPageService.countByChapterIds(chapterIds);
        }
        // 真实已入库(图片 url 全部就绪)的章节数
        int imageDoneChapters = chapterMapper.countImageDoneChapters(bookId);
        long done = chapterCount != null && chapterCount > 0 ? imageDoneChapters : 0;

        Book bookUpdate = new Book();
        bookUpdate.setId(bookId);
        bookUpdate.setTotalChapterCount(chapterCount);
        bookUpdate.setTotalImageCount(imageCount);
        bookUpdate.setImageDoneCount(done);
        bookUpdate.setPendingChapterCount(chapterCount == null ? 0 : chapterCount - done);

        // 入库状态(基于真实图片 url): 全部章节图片就绪 -> 置 2(已入库);
        // 原本已是"全部完成"(3)则保留; 入库中途(5)或部分失败则回到章节已爬取(1)
        if (chapterCount != null && chapterCount > 0 && done == chapterCount) {
            Book cur = bookMapper.selectById(bookId);
            Integer cs = cur != null ? cur.getCrawlStatus() : null;
            if (cs == null || cs <= 1 || cs == IkanmhConstants.STATUS_IMAGE_PROCESSING) {
                bookUpdate.setCrawlStatus(IkanmhConstants.STATUS_IMAGE_DONE);
            }
        } else {
            // 未全部就绪: 清除"入库中"(5), 回到"章节已爬取"(1)
            Book cur = bookMapper.selectById(bookId);
            Integer cs = cur != null ? cur.getCrawlStatus() : null;
            if (cs != null && cs == 5) {
                bookUpdate.setCrawlStatus(IkanmhConstants.STATUS_CHAPTER_DONE);
            }
        }
        bookMapper.updateById(bookUpdate);
    }
}
