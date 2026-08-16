package com.man3.utils.crawler.external.yuyumh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.YuyumhChapter;
import com.man3.entity.YuyumhComic;
import com.man3.entity.YuyumhPage;
import com.man3.mapper.YuyumhChapterMapper;
import com.man3.mapper.YuyumhMapper;
import com.man3.mapper.YuyumhPageMapper;
import com.man3.service.ExternalComicIngestService;
import com.man3.service.ExternalImageStorageService;
import com.man3.utils.ExternalSiteHttpClient;
import com.man3.utils.crawler.external.ExternalComicIngestCrawler;
import com.man3.utils.crawler.external.ExternalCrawlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Wuwu-site rules. A chapter is complete only after every image is verified locally. */
@Component
@Slf4j
public class YuyumhIngestCrawler implements ExternalComicIngestCrawler {
    private static final String BASE_URL = "https://www.comicbox.xyz";
    private static final Pattern CHAPTER_ID = Pattern.compile("/free-chapter/(\\d+)");
    private static final Pattern PAGE_NO = Pattern.compile("[?&]page=(\\d+)");
    private static final Pattern BMI_CACHE_KEY = Pattern.compile("BMI_CACHE_KEY\\s*=\\s*['\\\"]([^'\\\"]+)");
    private final ExternalSiteHttpClient http;
    private final YuyumhMapper comicMapper;
    private final YuyumhChapterMapper chapterMapper;
    private final YuyumhPageMapper pageMapper;
    private final ExternalCrawlExecutor executor;
    private final ExternalImageStorageService imageStorage;

    public YuyumhIngestCrawler(ExternalSiteHttpClient http, YuyumhMapper comicMapper,
                                YuyumhChapterMapper chapterMapper, YuyumhPageMapper pageMapper,
                                ExternalCrawlExecutor executor, ExternalImageStorageService imageStorage) {
        this.http = http;
        this.comicMapper = comicMapper;
        this.chapterMapper = chapterMapper;
        this.pageMapper = pageMapper;
        this.executor = executor;
        this.imageStorage = imageStorage;
    }

    @Override public String source() { return "yuyumh"; }

    @Override
    public void crawlChapters(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception {
        List<YuyumhComic> comics = eligibleComics(externalComicId);
        state.total(comics.size());
        executor.execute(comics, workers, state.stopToken(), comic -> syncChapters(comic, state));
    }

    @Override
    public void crawlPages(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception {
        List<YuyumhChapter> chapters = new ArrayList<>();
        for (YuyumhComic comic : eligibleComics(externalComicId)) {
            chapters.addAll(chapterMapper.selectList(new LambdaQueryWrapper<YuyumhChapter>()
                    .eq(YuyumhChapter::getExternalComicId, comic.getId())
                    .in(YuyumhChapter::getCrawlStatus, 0, -1, 5)));
        }
        state.total(chapters.size());
        executor.execute(chapters, workers, state.stopToken(), chapter -> syncPages(chapter, state));
    }

    private List<YuyumhComic> eligibleComics(Long externalComicId) {
        LambdaQueryWrapper<YuyumhComic> query = new LambdaQueryWrapper<YuyumhComic>()
                .eq(YuyumhComic::getIsSame, 0).orderByAsc(YuyumhComic::getId);
        if (externalComicId != null) query.eq(YuyumhComic::getId, externalComicId);
        return comicMapper.selectList(query);
    }

    private void syncChapters(YuyumhComic comic, ExternalComicIngestService.IngestState state) {
        try {
            Document first = http.get(comic.getSourceUrl());
            int maxPage = 1;
            for (Element link : first.select("a[href*='page=']")) {
                Matcher matcher = PAGE_NO.matcher(link.attr("href"));
                if (matcher.find()) maxPage = Math.max(maxPage, Integer.parseInt(matcher.group(1)));
            }
            Map<String, Element> chapterLinks = new LinkedHashMap<>();
            for (int page = 1; page <= maxPage && !state.shouldStop(); page++) {
                Document doc = page == 1 ? first : http.get(comic.getSourceUrl() + "?id=" + comic.getSourceBookId() + "&page=" + page);
                for (Element link : doc.select("a.sp-chapter-item[href*='/free-chapter/']")) {
                    Matcher matcher = CHAPTER_ID.matcher(link.attr("href"));
                    if (!matcher.find()) continue;
                    chapterLinks.putIfAbsent(matcher.group(1), link);
                }
            }
            if (chapterLinks.isEmpty()) throw new IllegalStateException("No chapters found");
            int chapterNo = 0;
            for (Map.Entry<String, Element> entry : chapterLinks.entrySet()) {
                chapterNo++;
                String sourceId = entry.getKey();
                Element link = entry.getValue();
                YuyumhChapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<YuyumhChapter>()
                        .eq(YuyumhChapter::getSourceChapterId, sourceId));
                if (chapter == null) {
                    chapter = new YuyumhChapter();
                    chapter.setExternalComicId(comic.getId());
                    chapter.setSourceChapterId(sourceId);
                    chapter.setChapterNo(chapterNo);
                    chapter.setTitle(link.text().trim());
                    chapter.setChapterUrl(BASE_URL + link.attr("href"));
                    chapter.setImageCount(0);
                    chapter.setCrawlStatus(0);
                    chapter.setCreatedAt(LocalDateTime.now());
                    chapterMapper.insert(chapter);
                } else {
                    chapter.setChapterNo(chapterNo);
                    chapter.setTitle(link.text().trim());
                    chapter.setChapterUrl(BASE_URL + link.attr("href"));
                    chapterMapper.updateById(chapter);
                }
            }
            comic.setChapterCount(chapterNo);
            comicMapper.updateById(comic);
            state.success(comic.getName());
        } catch (Exception e) {
            state.failed(comic.getName());
        }
    }

    private void syncPages(YuyumhChapter chapter, ExternalComicIngestService.IngestState state) {
        try {
            chapter.setCrawlStatus(5);
            chapterMapper.updateById(chapter);
            ExternalSiteHttpClient.PageResponse pageResponse = http.getWithCookies(chapter.getChapterUrl());
            Document doc = pageResponse.getDocument();
            String cacheKey = bmiCacheKey(doc);
            List<String> urls = new ArrayList<>();
            for (Element image : doc.select("div.cropped[data-src]")) {
                String url = image.absUrl("data-src");
                if (!url.isEmpty()) urls.add(url);
            }
            if (urls.isEmpty()) throw new IllegalStateException("No images found");

            pageMapper.delete(new LambdaQueryWrapper<YuyumhPage>().eq(YuyumhPage::getChapterId, chapter.getId()));
            imageStorage.deleteYuyumhChapter(chapter.getId());
            int pageNo = 0;
            for (String url : urls) {
                pageNo++;
                ExternalImageStorageService.StoredImage stored = imageStorage.downloadYuyumhImage(
                        chapter.getId(), pageNo, url, chapter.getChapterUrl(), pageResponse.getCookies(), cacheKey);
                YuyumhPage page = new YuyumhPage();
                page.setChapterId(chapter.getId());
                page.setPageNo(pageNo);
                page.setImgUrl(url);
                page.setFileSize(stored.getFileSize());
                page.setImgWidth(stored.getWidth());
                page.setImgHeight(stored.getHeight());
                page.setLocalPath(stored.getLocalPath());
                page.setDownloadStatus(1);
                page.setCreatedAt(LocalDateTime.now());
                pageMapper.insert(page);
            }
            chapter.setImageCount(urls.size());
            chapter.setCrawlStatus(1);
            chapter.setCrawlTime(LocalDateTime.now());
            chapterMapper.updateById(chapter);
            state.success(chapter.getTitle());
        } catch (Exception e) {
            log.warn("Yuyumh image ingest failed: chapterId={}, title={}, reason={}",
                    chapter.getId(), chapter.getTitle(), e.getMessage());
            pageMapper.delete(new LambdaQueryWrapper<YuyumhPage>().eq(YuyumhPage::getChapterId, chapter.getId()));
            try {
                imageStorage.deleteYuyumhChapter(chapter.getId());
            } catch (Exception ignore) {
                // The chapter remains failed, so stale files cannot be served by the reader.
            }
            chapter.setCrawlStatus(-1);
            chapterMapper.updateById(chapter);
            state.failed(chapter.getTitle());
        }
    }

    private String bmiCacheKey(Document doc) {
        for (Element script : doc.select("script")) {
            Matcher matcher = BMI_CACHE_KEY.matcher(script.data());
            if (matcher.find()) return matcher.group(1);
        }
        // The site's fallback is the current date. This keeps old pages crawlable if its inline assignment is omitted.
        return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }
}
