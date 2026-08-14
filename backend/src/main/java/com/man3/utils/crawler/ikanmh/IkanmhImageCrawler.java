package com.man3.utils.crawler.ikanmh;

import com.man3.config.IkanmhProperties;
import com.man3.entity.Chapter;
import com.man3.service.BookPageService;
import com.man3.service.ChapterService;
import com.man3.utils.CrawlerUtils;
import com.man3.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ikanmh 漫画章节图片爬虫
 * <p>
 * 独立于详情爬虫, 可单独触发:
 * 遍历子表中图片未爬取的章节, 进入阅读页解析真实图片地址填充孙表。
 * 仅接受 {@link #IMAGE_PATH_MARK} 路径下的图片, 排除广告图/占位图。
 */
@Slf4j
@Component
public class IkanmhImageCrawler {

    /** 真实图片路径标记(图片服务器独立于主站) */
    private static final String IMAGE_PATH_MARK = "/static/upload/book/";

    /** 图片选择器: 懒加载 img 的真实地址在 data-original 属性中 */
    private static final String IMG_SELECTOR = "img[data-original]";

    private final IkanmhProperties props;
    private final HttpClientUtils httpClientUtils;
    private final ChapterService chapterService;
    private final BookPageService bookPageService;

    public IkanmhImageCrawler(IkanmhProperties props, HttpClientUtils httpClientUtils,
                              ChapterService chapterService, BookPageService bookPageService) {
        this.props = props;
        this.httpClientUtils = httpClientUtils;
        this.chapterService = chapterService;
        this.bookPageService = bookPageService;
    }

    /**
     * 爬取所有图片未爬取的章节, 返回成功章节数
     */
    public int crawlAllImages() {
        return crawlAllImages(-1);
    }

    /**
     * 爬取图片未爬取的章节, 返回成功章节数
     *
     * @param limit 最多处理条数, <=0 表示不限
     */
    public int crawlAllImages(int limit) {
        List<Chapter> chapters = chapterService.listUncrawledImages(limit);
        log.info("待爬图片章节数量: {}", chapters.size());
        int success = 0;
        int failed = 0;
        for (Chapter chapter : chapters) {
            try {
                crawlOneChapter(chapter);
                success++;
            } catch (Exception e) {
                failed++;
                chapterService.updateImageResult(chapter.getId(), 0, false);
                log.error("章节[{}({})]图片解析失败: {}", chapter.getTitle(), chapter.getSourceChapterId(), e.getMessage());
            }
            CrawlerUtils.sleep(props.getDetailRequestIntervalMs());
        }
        log.info("===== 图片爬取结束, 成功{}章, 失败{}章 =====", success, failed);
        return success;
    }

    /**
     * 爬取单个章节的全部图片地址并写入孙表
     */
    private void crawlOneChapter(Chapter chapter) throws Exception {
        String url = props.getBaseUrl() + IkanmhConstants.CHAPTER_PATH + chapter.getSourceChapterId();
        Document doc = httpClientUtils.get(url);

        List<String> imgUrls = new ArrayList<>();
        Elements imgs = doc.select(IMG_SELECTOR);
        for (Element img : imgs) {
            String imgUrl = img.attr("data-original").trim();
            // 仅保留真实漫画图, 过滤广告图/占位图/外链
            if (imgUrl.contains(IMAGE_PATH_MARK)) {
                imgUrls.add(imgUrl);
            }
        }
        if (imgUrls.isEmpty()) {
            throw new IllegalStateException("未解析到有效图片(可能页面结构变化或为广告页)");
        }

        bookPageService.syncPages(chapter.getId(), imgUrls);
        chapterService.updateImageResult(chapter.getId(), imgUrls.size(), true);
        log.info("章节[{}({})]解析{}张图片完成", chapter.getTitle(), chapter.getSourceChapterId(), imgUrls.size());
    }
}
