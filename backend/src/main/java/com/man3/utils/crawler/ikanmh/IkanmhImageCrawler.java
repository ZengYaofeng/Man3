package com.man3.utils.crawler.ikanmh;

import com.man3.config.IkanmhProperties;
import com.man3.entity.Chapter;
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

    private final IkanmhProperties props;
    private final HttpClientUtils http;
    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;
    private final BookPageService bookPageService;

    /** 并发章节处理线程池(每章仅 1 次章节页请求, 不逐图请求源站) */
    private ExecutorService chapterPool;

    /** 统计: 成功/失败章节数 */
    private final AtomicLong doneCount = new AtomicLong(0);
    private final AtomicLong failCount = new AtomicLong(0);

    /** 正在处理中(已领取未完成的)的章节ID, 用于避免同一章被重复处理 */
    private final Map<Long, Boolean> claimed = new ConcurrentHashMap<>();

    public IkanmhImageCrawler(IkanmhProperties props, HttpClientUtils http,
                              ChapterService chapterService, ChapterMapper chapterMapper,
                              BookPageService bookPageService) {
        this.props = props;
        this.http = http;
        this.chapterService = chapterService;
        this.chapterMapper = chapterMapper;
        this.bookPageService = bookPageService;
    }

    /** 请求外部停止(由控制器调用) */
    public static void requestStop() {
        stopFlag = true;
    }

    public void crawlAllImages() {
        if (running) {
            log.warn("图片爬虫已在运行中, 忽略重复启动");
            return;
        }
        running = true;
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
                    chapterPool.submit(() -> processChapter(ch));
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
            running = false;
            if (chapterPool != null) {
                chapterPool.shutdownNow();
            }
            log.info("图片爬虫结束: 成功章节={}, 失败章节={}", doneCount.get(), failCount.get());
        }
    }

    /** 处理单个章节: 解析阅读页图片URL, 并发解析尺寸并入库 */
    private void processChapter(Chapter chapter) {
        try {
            if (stopFlag) {
                return;
            }
            long t0 = System.currentTimeMillis();
            String url = props.getBaseUrl() + IkanmhConstants.CHAPTER_PATH + chapter.getSourceChapterId();
            Document doc = http.get(url);
            if (doc == null) {
                // 源站访问失败(网络/限流), 回滚为待爬以便重试
                log.warn("章节 {} 阅读页获取失败(回滚待爬): {}", chapter.getId(), url);
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
                chapterService.updateImageResult(chapter.getId(), 0, false);
                failCount.incrementAndGet();
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
            chapterService.updateImageResult(chapter.getId(), n, true);

        } catch (Exception e) {
            // 网络/解析类异常: 源站抖动或临时不可达, 回滚为待爬以便后续重试, 避免污染数据
            log.warn("章节图片处理异常 id={} (回滚待爬): {}", chapter.getId(), e.getMessage());
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
}
