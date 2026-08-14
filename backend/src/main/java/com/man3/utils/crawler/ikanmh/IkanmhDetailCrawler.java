package com.man3.utils.crawler.ikanmh;

import com.man3.config.IkanmhProperties;
import com.man3.entity.Book;
import com.man3.entity.Chapter;
import com.man3.service.BookService;
import com.man3.service.ChapterService;
import com.man3.utils.CrawlerUtils;
import com.man3.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * ikanmh 漫画详情 + 章节爬虫
 * 遍历主表中未爬详情的漫画, 进入详情页解析完整信息并抓取全部章节填充子表
 */
@Slf4j
@Component
public class IkanmhDetailCrawler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IkanmhProperties props;
    private final HttpClientUtils httpClientUtils;
    private final BookService bookService;
    private final ChapterService chapterService;

    public IkanmhDetailCrawler(IkanmhProperties props, HttpClientUtils httpClientUtils,
                               BookService bookService, ChapterService chapterService) {
        this.props = props;
        this.httpClientUtils = httpClientUtils;
        this.bookService = bookService;
        this.chapterService = chapterService;
    }

    /**
     * 爬取主表中所有未爬详情的漫画, 返回成功数
     */
    public int crawlAllDetails() {
        return crawlAllDetails(-1);
    }

    /**
     * 爬取主表中未爬/失败详情的漫画, 实现断点续爬(已成功=1的不会重复处理), 返回成功数
     *
     * @param limit 最多处理条数, <=0 表示不限
     */
    public int crawlAllDetails(int limit) {
        // 断点续爬: 只处理 未爬(0) 与 上次失败(-1) 的漫画, 已完成(1)自动跳过, 不重复入库也不浪费请求
        List<Integer> statuses = Arrays.asList(
                IkanmhConstants.STATUS_NOT_CRAWLED, IkanmhConstants.STATUS_FAILED);
        List<Book> books = bookService.listByCrawlStatuses(statuses);
        int total = books.size();
        if (limit > 0 && total > limit) {
            books = books.subList(0, limit);
        }
        log.info("待爬详情漫画数量: {}, 本次执行: {}", total, books.size());
        int success = 0;
        int failed = 0;
        for (Book book : books) {
            try {
                crawlOneBook(book);
                success++;
            } catch (Exception e) {
                failed++;
                bookService.updateCrawlStatus(book.getId(), IkanmhConstants.STATUS_FAILED);
                log.error("漫画[{}]({})详情爬取失败: {}", book.getName(), book.getSourceBookId(), e.getMessage());
            }
            CrawlerUtils.sleep(props.getDetailRequestIntervalMs());
        }
        log.info("===== 详情爬取结束, 成功{}条, 失败{}条 =====", success, failed);
        return success;
    }

    /**
     * 爬取单个漫画详情并同步章节
     */
    private void crawlOneBook(Book book) throws Exception {
        String url = props.getBaseUrl() + IkanmhConstants.BOOK_PATH + book.getSourceBookId();
        Document doc = httpClientUtils.get(url);

        Element info = doc.selectFirst("section.banner_detail div.info");
        if (info == null) {
            throw new IllegalStateException("详情页结构异常, 未找到信息区");
        }

        // 1. 基础信息
        Book update = new Book();
        update.setId(book.getId());
        update.setName(info.selectFirst("h1").text().trim());

        Elements subtitles = info.select("p.subtitle");
        if (subtitles.size() > 0) {
            update.setAlias(CrawlerUtils.stripPrefix(subtitles.get(0).text(), "别名"));
        }
        if (subtitles.size() > 1) {
            update.setAuthor(CrawlerUtils.stripPrefix(subtitles.get(1).text(), "作者"));
        }

        // 状态/地区/更新时间/点击量
        for (Element block : info.select("p.tip span.block")) {
            String t = block.text();
            if (t.contains("状态")) {
                update.setStatus(CrawlerUtils.stripPrefix(t, "状态"));
            } else if (t.contains("地区")) {
                update.setRegion(CrawlerUtils.stripPrefix(t, "地区"));
            } else if (t.contains("更新时间")) {
                update.setUpdateTime(parseDate(CrawlerUtils.stripPrefix(t, "更新时间")));
            } else if (t.contains("点击")) {
                update.setClicks(CrawlerUtils.extractNumber(t));
            }
        }

        // 标签: <p class="tip"><span class="block">标签：<a href="/booklist/?tag=都市">都市</a></span></p>
        List<String> tags = new ArrayList<>();
        for (Element a : info.select("a[href*='tag=']")) {
            String tag = a.text().trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        update.setTags(String.join(",", tags));

        // 简介
        Element content = info.selectFirst("p.content");
        if (content != null) {
            update.setDescription(content.text().trim());
        }

        // 封面(以详情页为准)
        Element coverImg = doc.selectFirst("section.banner_detail div.cover img");
        if (coverImg != null) {
            String cover = coverImg.absUrl("src");
            if (!cover.isEmpty()) {
                update.setCoverUrl(cover);
            }
        }

        bookService.updateDetail(update);

        // 2. 章节列表(全部章节已渲染在 HTML 中, 无需额外请求)
        //    断点续爬优化: 若该漫画章节表已有数据, 说明上次已同步过章节, 直接跳过重新解析章节,
        //    既不重复入库也不浪费重复请求; 仅当章节表为空时才解析并同步
        long existChapters = chapterService.countByBookId(book.getId());
        int chapterCount = (int) existChapters;
        if (existChapters == 0) {
            Elements chapterLinks = doc.select("#detail-list-select li a[href*='/chapter/']");
            List<Chapter> chapters = parseChapters(book.getId(), chapterLinks);
            if (!chapters.isEmpty()) {
                chapterService.syncChapters(book.getId(), chapters);
                chapterCount = chapters.size();
            }
        } else {
            log.info("漫画[{}]章节表已有{}条, 跳过重复同步", book.getName(), existChapters);
        }

        // 3. 标记完成
        bookService.updateCrawlStatus(book.getId(), IkanmhConstants.STATUS_CHAPTER_DONE);
        log.info("漫画[{}]详情完成(章节{}条)", book.getName(), chapterCount);
    }

    /**
     * 解析章节列表, 按来源章节ID升序编号(章节ID越大越新)
     */
    private List<Chapter> parseChapters(Long bookId, Elements links) {
        List<Chapter> chapters = new ArrayList<>();
        for (Element a : links) {
            String href = a.attr("href");               // /chapter/57217
            String chapterId = href.replace(IkanmhConstants.CHAPTER_PATH, "").trim();
            if (chapterId.isEmpty() || !chapterId.matches("\\d+")) {
                continue;
            }
            Chapter chapter = new Chapter();
            chapter.setBookId(bookId);
            chapter.setSourceChapterId(chapterId);
            chapter.setChapterUrl(props.getBaseUrl() + href);
            chapter.setTitle(a.text().trim());
            chapter.setImageCount(0);
            chapters.add(chapter);
        }
        // 按章节ID升序: 第1話在前
        chapters.sort(Comparator.comparing(Chapter::getSourceChapterId,
                Comparator.comparingLong(Long::parseLong)));
        for (int i = 0; i < chapters.size(); i++) {
            chapters.get(i).setChapterNo(i + 1);
        }
        return chapters;
    }

    /**
     * 解析 "2026-08-14" 格式的日期
     */
    private LocalDateTime parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}
