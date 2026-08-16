package com.man3.utils.crawler.external.niaoniaomh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.NiaoniaomhChapter;
import com.man3.entity.NiaoniaomhComic;
import com.man3.entity.NiaoniaomhPage;
import com.man3.mapper.NiaoniaomhChapterMapper;
import com.man3.mapper.NiaoniaomhMapper;
import com.man3.mapper.NiaoniaomhPageMapper;
import com.man3.service.ExternalComicIngestService;
import com.man3.utils.ExternalSiteHttpClient;
import com.man3.utils.crawler.external.ExternalComicIngestCrawler;
import com.man3.utils.crawler.external.ExternalCrawlExecutor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bird-site rules. Common worker scheduling is implemented in ExternalCrawlExecutor. */
@Component
public class NiaoniaomhIngestCrawler implements ExternalComicIngestCrawler {
    private static final String BASE_URL = "https://nnhm7.com";
    private static final Pattern CHAPTER_ID = Pattern.compile("chapter-(\\d+)\\.html");
    private final ExternalSiteHttpClient http;
    private final NiaoniaomhMapper comicMapper;
    private final NiaoniaomhChapterMapper chapterMapper;
    private final NiaoniaomhPageMapper pageMapper;
    private final ExternalCrawlExecutor executor;

    public NiaoniaomhIngestCrawler(ExternalSiteHttpClient http, NiaoniaomhMapper comicMapper,
                                   NiaoniaomhChapterMapper chapterMapper, NiaoniaomhPageMapper pageMapper,
                                   ExternalCrawlExecutor executor) {
        this.http = http;
        this.comicMapper = comicMapper;
        this.chapterMapper = chapterMapper;
        this.pageMapper = pageMapper;
        this.executor = executor;
    }

    @Override public String source() { return "niaoniaomh"; }

    @Override
    public void crawlChapters(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception {
        List<NiaoniaomhComic> comics = eligibleComics(externalComicId);
        state.total(comics.size());
        executor.execute(comics, workers, state.stopToken(), comic -> syncChapters(comic, state));
    }

    @Override
    public void crawlPages(ExternalComicIngestService.IngestState state, int workers, Long externalComicId) throws Exception {
        List<NiaoniaomhChapter> chapters = new ArrayList<>();
        for (NiaoniaomhComic comic : eligibleComics(externalComicId)) {
            chapters.addAll(chapterMapper.selectList(new LambdaQueryWrapper<NiaoniaomhChapter>()
                    .eq(NiaoniaomhChapter::getExternalComicId, comic.getId())
                    .in(NiaoniaomhChapter::getCrawlStatus, 0, -1, 5)));
        }
        state.total(chapters.size());
        executor.execute(chapters, workers, state.stopToken(), chapter -> syncPages(chapter, state));
    }

    private List<NiaoniaomhComic> eligibleComics(Long externalComicId) {
        LambdaQueryWrapper<NiaoniaomhComic> query = new LambdaQueryWrapper<NiaoniaomhComic>()
                .eq(NiaoniaomhComic::getIsSame, 0).orderByAsc(NiaoniaomhComic::getId);
        if (externalComicId != null) query.eq(NiaoniaomhComic::getId, externalComicId);
        return comicMapper.selectList(query);
    }

    private void syncChapters(NiaoniaomhComic comic, ExternalComicIngestService.IngestState state) {
        try {
            Document doc = http.get(comic.getSourceUrl());
            Elements links = doc.select("ul#mh-chapter-list-ol-0 a[href*='chapter-']");
            List<Element> ordered = new ArrayList<>(links);
            Collections.reverse(ordered);
            int number = 0;
            for (Element link : ordered) {
                String href = link.attr("href");
                Matcher matcher = CHAPTER_ID.matcher(href);
                if (!matcher.find()) continue;
                number++;
                String sourceId = matcher.group(1);
                NiaoniaomhChapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<NiaoniaomhChapter>()
                        .eq(NiaoniaomhChapter::getSourceChapterId, sourceId));
                if (chapter == null) {
                    chapter = new NiaoniaomhChapter();
                    chapter.setExternalComicId(comic.getId());
                    chapter.setSourceChapterId(sourceId);
                    chapter.setChapterNo(number);
                    chapter.setTitle(link.text().trim());
                    chapter.setChapterUrl(BASE_URL + href);
                    chapter.setImageCount(0);
                    chapter.setCrawlStatus(0);
                    chapter.setCreatedAt(LocalDateTime.now());
                    chapterMapper.insert(chapter);
                } else {
                    chapter.setChapterNo(number);
                    chapter.setTitle(link.text().trim());
                    chapter.setChapterUrl(BASE_URL + href);
                    chapterMapper.updateById(chapter);
                }
            }
            comic.setChapterCount(number);
            comicMapper.updateById(comic);
            state.success(comic.getName());
        } catch (Exception e) {
            state.failed(comic.getName());
        }
    }

    private void syncPages(NiaoniaomhChapter chapter, ExternalComicIngestService.IngestState state) {
        try {
            chapter.setCrawlStatus(5);
            chapterMapper.updateById(chapter);
            Document doc = http.get(chapter.getChapterUrl());
            Elements images = doc.select(".img-wrap img, img[data-src]");
            int pageNo = 0;
            for (Element image : images) {
                String url = firstUrl(image);
                if (url.isEmpty()) continue;
                pageNo++;
                if (pageMapper.selectOne(new LambdaQueryWrapper<NiaoniaomhPage>()
                        .eq(NiaoniaomhPage::getChapterId, chapter.getId())
                        .eq(NiaoniaomhPage::getPageNo, pageNo)) == null) {
                    NiaoniaomhPage page = new NiaoniaomhPage();
                    page.setChapterId(chapter.getId());
                    page.setPageNo(pageNo);
                    page.setImgUrl(url);
                    page.setDownloadStatus(0);
                    page.setCreatedAt(LocalDateTime.now());
                    pageMapper.insert(page);
                }
            }
            if (pageNo == 0) throw new IllegalStateException("No images found");
            chapter.setImageCount(pageNo);
            chapter.setCrawlStatus(1);
            chapter.setCrawlTime(LocalDateTime.now());
            chapterMapper.updateById(chapter);
            state.success(chapter.getTitle());
        } catch (Exception e) {
            chapter.setCrawlStatus(-1);
            chapterMapper.updateById(chapter);
            state.failed(chapter.getTitle());
        }
    }

    private String firstUrl(Element image) {
        String[] attrs = {"data-original", "data-src", "src"};
        for (String attr : attrs) {
            String value = image.absUrl(attr);
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
