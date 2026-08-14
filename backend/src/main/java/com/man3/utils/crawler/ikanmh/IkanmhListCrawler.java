package com.man3.utils.crawler.ikanmh;

import com.man3.config.IkanmhProperties;
import com.man3.entity.Book;
import com.man3.service.BookService;
import com.man3.utils.CrawlerUtils;
import com.man3.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ikanmh 漫画列表爬虫
 * 遍历 /booklist?page=1..N, 解析每个漫画卡片入库主表 book
 */
@Slf4j
@Component
public class IkanmhListCrawler {

    /** 匹配评分类名 star-数字 */
    private static final Pattern STAR_PATTERN = Pattern.compile("star-(\\d)");

    /** 列表爬虫停止标志(收到停止指令后置 true, 翻页结束前退出) */
    public static volatile boolean stopFlag = false;

    /** 请求外部停止爬虫 */
    public static void requestStop() {
        stopFlag = true;
    }

    private final IkanmhProperties props;
    private final HttpClientUtils httpClientUtils;
    private final BookService bookService;

    public IkanmhListCrawler(IkanmhProperties props, HttpClientUtils httpClientUtils, BookService bookService) {
        this.props = props;
        this.httpClientUtils = httpClientUtils;
        this.bookService = bookService;
    }

    /**
     * 爬取全部列表页, 返回本次处理的漫画数量
     */
    public int crawlAllBooks() {
        int total = 0;
        int page = 1;
        stopFlag = false;
        while (true) {
            if (stopFlag) {
                log.info("收到停止指令, 中断列表爬取(已处理{}条)", total);
                break;
            }
            String url = props.getBaseUrl() + IkanmhConstants.BOOKLIST_PATH + "?page=" + page;
            Document doc;
            try {
                doc = httpClientUtils.get(url);
            } catch (Exception e) {
                log.error("列表页抓取失败, 终止翻页: {} - {}", url, e.getMessage());
                break;
            }

            Elements items = doc.select("ul.mh-list > li");
            if (items.isEmpty()) {
                log.info("第{}页无数据, 翻页结束", page);
                break;
            }

            int pageCount = 0;
            for (Element li : items) {
                Book book = parseItem(li);
                if (book != null) {
                    bookService.upsertFromList(book);
                    pageCount++;
                }
            }
            total += pageCount;
            log.info("列表页第{}页完成, 本页{}条, 累计{}条", page, pageCount, total);

            // 终止条件: 已无下一页 或 本页不足一页(最后一页)
            Element nextPage = doc.selectFirst("a#nextPage");
            if (nextPage == null || nextPage.attr("href").isEmpty() || items.size() < IkanmhConstants.PAGE_SIZE) {
                break;
            }
            page++;
            CrawlerUtils.sleep(props.getListRequestIntervalMs());
        }
        log.info("===== 列表爬取结束, 共处理 {} 条 =====", total);
        return total;
    }

    /**
     * 解析单个漫画卡片
     */
    private Book parseItem(Element li) {
        try {
            Element link = li.selectFirst("a[href^=\"/book/\"]");
            if (link == null) {
                return null;
            }
            String href = link.attr("href");               // /book/1224
            String bookId = href.replace("/book/", "").trim();
            if (bookId.isEmpty() || !bookId.matches("\\d+")) {
                return null;
            }

            Book book = new Book();
            book.setSourceBookId(bookId);
            book.setSourceUrl(props.getBaseUrl() + href);

            // 名称
            Element titleA = li.selectFirst("h2.title a");
            if (titleA != null) {
                book.setName(titleA.attr("title"));
                if (book.getName() == null || book.getName().isEmpty()) {
                    book.setName(titleA.text());
                }
            }

            // 封面: <p class="mh-cover" style="background-image: url(...)">
            Element cover = li.selectFirst("p.mh-cover");
            if (cover != null) {
                book.setCoverUrl(CrawlerUtils.extractUrlFromStyle(cover.attr("style")));
            }

            // 评分: <span class="mh-star-line star-5"> (注意 star- 会出现在类名 mh-star-line 中, 需用正则精确匹配 star-数字)
            Element star = li.selectFirst("span.mh-star-line");
            if (star != null) {
                Matcher m = STAR_PATTERN.matcher(star.className());
                if (m.find()) {
                    book.setScore(new BigDecimal(m.group(1)));
                }
            }

            // 简介摘要
            Element desc = li.selectFirst("p.chapter");
            if (desc != null) {
                book.setDescription(desc.text().trim());
            }
            return book;
        } catch (Exception e) {
            log.warn("列表卡片解析失败: {}", e.getMessage());
            return null;
        }
    }
}
