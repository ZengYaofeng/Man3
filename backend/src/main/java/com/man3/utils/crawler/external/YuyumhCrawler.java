package com.man3.utils.crawler.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.YuyumhComic;
import com.man3.mapper.YuyumhMapper;
import com.man3.service.ExternalComicCrawlService;
import com.man3.service.ExternalComicMatcher;
import com.man3.utils.ExternalSiteHttpClient;
import com.man3.utils.ComicNameNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 汙汙漫畫：抓取漫画列表和详情元数据，不请求章节和图片内容。 */
@Slf4j
@Component
public class YuyumhCrawler {

    private static final String BASE_URL = "https://www.comicbox.xyz";
    private static final long REQUEST_INTERVAL_MS = 250L;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private final ExternalSiteHttpClient http;
    private final YuyumhMapper mapper;
    private final ExternalComicMatcher matcher;

    public YuyumhCrawler(ExternalSiteHttpClient http, YuyumhMapper mapper, ExternalComicMatcher matcher) {
        this.http = http;
        this.mapper = mapper;
        this.matcher = matcher;
    }

    public void crawlAll(ExternalComicCrawlService.CrawlState state) throws Exception {
        int page = 1;
        while (true) {
            Document doc = http.get(BASE_URL + "/booklist?page=" + page);
            Elements cards = doc.select("a.sp-booklist-card[href^=/book/]");
            if (cards.isEmpty()) break;

            int pageCount = 0;
            for (Element card : cards) {
                YuyumhComic comic = parseListItem(card);
                if (comic == null) continue;
                fillDetail(comic);
                upsert(comic);
                state.processed(comic.getName());
                pageCount++;
                sleep(REQUEST_INTERVAL_MS);
            }
            state.pageCompleted();
            log.info("汙汙漫畫第{}页完成，本页{}条，累计{}条", page, pageCount, state.toMap().get("processed"));

            Element next = doc.selectFirst("a#nextPage[href]");
            if (next == null || next.attr("href").trim().isEmpty()) break;
            page++;
        }
        matcher.refreshYuyumhMatches();
    }

    private YuyumhComic parseListItem(Element card) {
        String href = card.attr("href").trim();
        String sourceId = href.replaceFirst("^/book/", "");
        String name = card.attr("title").trim();
        if (sourceId.isEmpty() || name.isEmpty()) return null;

        YuyumhComic comic = new YuyumhComic();
        comic.setSourceBookId(sourceId);
        comic.setName(name);
        comic.setSourceUrl(BASE_URL + href);
        Element cover = card.selectFirst(".cropped[data-src]");
        if (cover != null) comic.setCoverUrl(cover.attr("data-src").trim());
        Element chapterMeta = card.selectFirst(".sp-booklist-meta");
        if (chapterMeta != null) comic.setChapterCount(extractNumber(chapterMeta.text()));
        Element status = card.selectFirst(".sp-book-end-badge");
        if (status != null) comic.setStatus(status.text().trim());
        return comic;
    }

    private void fillDetail(YuyumhComic comic) {
        try {
            Document doc = http.get(comic.getSourceUrl());
            Element title = doc.selectFirst(".sp-book-title");
            if (title != null && !title.text().trim().isEmpty()) comic.setName(title.text().trim());
            Element author = doc.selectFirst(".sp-book-author");
            if (author != null) comic.setAuthor(author.text().replaceFirst("^作者[:：]", "").trim());
            Elements tagNodes = doc.select(".sp-book-tags .sp-book-tag");
            if (!tagNodes.isEmpty()) comic.setTags(tagNodes.eachText().toString().replace("[", "").replace("]", ""));
            Element description = doc.selectFirst("meta[name=description]");
            if (description != null) comic.setDescription(description.attr("content").trim());
            Element cover = doc.selectFirst("meta[property=og:image]");
            if (cover != null && !cover.attr("content").trim().isEmpty()) comic.setCoverUrl(cover.attr("content").trim());
            Element chapterTotal = doc.selectFirst(".sp-chapters .sp-section-header span");
            if (chapterTotal != null) comic.setChapterCount(extractNumber(chapterTotal.text()));
        } catch (Exception e) {
            log.warn("汙汙漫畫详情抓取失败 {}: {}", comic.getSourceUrl(), e.getMessage());
        }
    }

    private void upsert(YuyumhComic comic) {
        comic.setNamePinyin(ComicNameNormalizer.toPinyin(comic.getName()));
        YuyumhComic existing = mapper.selectOne(new LambdaQueryWrapper<YuyumhComic>()
                .eq(YuyumhComic::getSourceBookId, comic.getSourceBookId()));
        comic.setCrawlTime(LocalDateTime.now());
        if (existing == null) mapper.insert(comic);
        else {
            comic.setId(existing.getId());
            mapper.updateById(comic);
        }
    }

    private Integer extractNumber(String value) {
        Matcher matcher = NUMBER_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
