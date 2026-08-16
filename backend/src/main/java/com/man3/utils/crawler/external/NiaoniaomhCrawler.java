package com.man3.utils.crawler.external;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.NiaoniaomhComic;
import com.man3.mapper.NiaoniaomhMapper;
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

/** 鸟鸟韩漫：抓取漫画列表及详情元数据，不抓取章节和图片。 */
@Slf4j
@Component
public class NiaoniaomhCrawler {

    private static final String BASE_URL = "https://nnhm7.com";
    private static final long REQUEST_INTERVAL_MS = 250L;

    private final ExternalSiteHttpClient http;
    private final NiaoniaomhMapper mapper;
    private final ExternalComicMatcher matcher;

    public NiaoniaomhCrawler(ExternalSiteHttpClient http, NiaoniaomhMapper mapper,
                             ExternalComicMatcher matcher) {
        this.http = http;
        this.mapper = mapper;
        this.matcher = matcher;
    }

    public void crawlAll(ExternalComicCrawlService.CrawlState state) throws Exception {
        int page = 1;
        while (true) {
            String url = page == 1 ? BASE_URL + "/comics" : BASE_URL + "/comics/all/ob/time/st/all/page/" + page;
            Document doc = http.get(url);
            Elements cards = doc.select("div.imgBox > ul.col_3_1 > li");
            if (cards.isEmpty()) break;

            int pageCount = 0;
            for (Element card : cards) {
                NiaoniaomhComic comic = parseListItem(card);
                if (comic == null) continue;
                fillDetail(comic);
                upsert(comic);
                state.processed(comic.getName());
                pageCount++;
                sleep(REQUEST_INTERVAL_MS);
            }
            state.pageCompleted();
            log.info("鸟鸟韩漫第{}页完成，本页{}条，累计{}条", page, pageCount, state.toMap().get("processed"));

            Element next = doc.selectFirst(".pagination-wrap a:matchesOwn(下一页)");
            if (next == null || next.attr("href").trim().isEmpty()) break;
            page++;
        }
        matcher.refreshNiaoniaomhMatches();
    }

    private NiaoniaomhComic parseListItem(Element card) {
        Element titleLink = card.selectFirst("a.txtA[href^=/comic/]");
        if (titleLink == null) return null;
        String href = titleLink.attr("href").trim();
        String sourceId = href.replaceFirst("^/comic/", "").replaceFirst("\\.html$", "");
        String name = titleLink.hasAttr("title") ? titleLink.attr("title").trim() : titleLink.text().trim();
        if (sourceId.isEmpty() || name.isEmpty()) return null;

        NiaoniaomhComic comic = new NiaoniaomhComic();
        comic.setSourceBookId(sourceId);
        comic.setName(name);
        comic.setSourceUrl(BASE_URL + href);
        Element image = card.selectFirst("a.ImgA img");
        if (image != null) comic.setCoverUrl(image.absUrl("src"));
        Element updated = card.selectFirst("span.info");
        if (updated != null) comic.setSourceUpdateText(updated.text().trim());
        return comic;
    }

    private void fillDetail(NiaoniaomhComic comic) {
        try {
            Document doc = http.get(comic.getSourceUrl());
            Element title = doc.selectFirst(".Introduct h1");
            if (title != null && !title.text().trim().isEmpty()) {
                comic.setName(title.text().replace("《", "").replace("》", "").trim());
            }
            Element cover = doc.selectFirst("#Cover img");
            if (cover != null && !cover.absUrl("src").isEmpty()) comic.setCoverUrl(cover.absUrl("src"));
            Element description = doc.selectFirst(".Introduct .txtDesc");
            if (description != null) comic.setDescription(description.text().replaceFirst("^(介绍|簡介)[:：]", "").trim());
            Element keywords = doc.selectFirst("meta[name=keywords]");
            if (keywords != null) comic.setTags(keywords.attr("content").trim());
        } catch (Exception e) {
            log.warn("鸟鸟韩漫详情抓取失败 {}: {}", comic.getSourceUrl(), e.getMessage());
        }
    }

    private void upsert(NiaoniaomhComic comic) {
        comic.setNamePinyin(ComicNameNormalizer.toPinyin(comic.getName()));
        NiaoniaomhComic existing = mapper.selectOne(new LambdaQueryWrapper<NiaoniaomhComic>()
                .eq(NiaoniaomhComic::getSourceBookId, comic.getSourceBookId()));
        comic.setCrawlTime(LocalDateTime.now());
        if (existing == null) mapper.insert(comic);
        else {
            comic.setId(existing.getId());
            mapper.updateById(comic);
        }
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
