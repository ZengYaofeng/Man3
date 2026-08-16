package com.man3.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.ExternalComic;
import com.man3.entity.ExternalComicChapter;
import com.man3.entity.ExternalComicPage;
import com.man3.entity.NiaoniaomhChapter;
import com.man3.entity.NiaoniaomhComic;
import com.man3.entity.NiaoniaomhPage;
import com.man3.entity.YuyumhChapter;
import com.man3.entity.YuyumhComic;
import com.man3.entity.YuyumhPage;
import com.man3.config.IkanmhProperties;
import com.man3.mapper.NiaoniaomhMapper;
import com.man3.mapper.NiaoniaomhChapterMapper;
import com.man3.mapper.NiaoniaomhPageMapper;
import com.man3.mapper.YuyumhMapper;
import com.man3.mapper.YuyumhChapterMapper;
import com.man3.mapper.YuyumhPageMapper;
import com.man3.service.ExternalComicCrawlService;
import com.man3.service.ExternalComicMatcher;
import com.man3.service.ExternalComicIngestService;
import com.man3.service.ExternalImageStorageService;
import com.man3.utils.crawler.ikanmh.IkanmhImageCrawler;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 鸟鸟韩漫、汙汙漫畫的独立主表列表、抓取及本地名称对比接口。 */
@RestController
@RequestMapping("/api/external-comics")
public class ExternalComicController {

    private final NiaoniaomhMapper niaoniaomhMapper;
    private final YuyumhMapper yuyumhMapper;
    private final NiaoniaomhChapterMapper niaoniaomhChapterMapper;
    private final YuyumhChapterMapper yuyumhChapterMapper;
    private final NiaoniaomhPageMapper niaoniaomhPageMapper;
    private final YuyumhPageMapper yuyumhPageMapper;
    private final ExternalComicCrawlService crawlService;
    private final ExternalComicMatcher matcher;
    private final ExternalComicIngestService ingestService;
    private final IkanmhProperties ikanmhProperties;
    private final ExternalImageStorageService imageStorage;

    public ExternalComicController(NiaoniaomhMapper niaoniaomhMapper, YuyumhMapper yuyumhMapper,
                                  NiaoniaomhChapterMapper niaoniaomhChapterMapper,
                                  YuyumhChapterMapper yuyumhChapterMapper,
                                  NiaoniaomhPageMapper niaoniaomhPageMapper,
                                  YuyumhPageMapper yuyumhPageMapper,
                                  ExternalComicCrawlService crawlService, ExternalComicMatcher matcher,
                                  ExternalComicIngestService ingestService, IkanmhProperties ikanmhProperties,
                                  ExternalImageStorageService imageStorage) {
        this.niaoniaomhMapper = niaoniaomhMapper;
        this.yuyumhMapper = yuyumhMapper;
        this.niaoniaomhChapterMapper = niaoniaomhChapterMapper;
        this.yuyumhChapterMapper = yuyumhChapterMapper;
        this.niaoniaomhPageMapper = niaoniaomhPageMapper;
        this.yuyumhPageMapper = yuyumhPageMapper;
        this.crawlService = crawlService;
        this.matcher = matcher;
        this.ingestService = ingestService;
        this.ikanmhProperties = ikanmhProperties;
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{source}/list")
    public Map<String, Object> list(@PathVariable String source,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer same,
                                    @RequestParam(required = false) Integer chapterMin,
                                    @RequestParam(required = false) Integer chapterMax,
                                    @RequestParam(required = false) Integer ingestStatus) {
        if ("niaoniaomh".equals(source)) return ok(page(source, niaoniaomhMapper, page, pageSize, keyword, same, chapterMin, chapterMax, ingestStatus));
        if ("yuyumh".equals(source)) return ok(page(source, yuyumhMapper, page, pageSize, keyword, same, chapterMin, chapterMax, ingestStatus));
        return fail("未知来源：" + source);
    }

    @GetMapping("/{source}/stats")
    public Map<String, Object> stats(@PathVariable String source) {
        if ("niaoniaomh".equals(source)) return ok(stats(niaoniaomhMapper));
        if ("yuyumh".equals(source)) return ok(stats(yuyumhMapper));
        return fail("未知来源：" + source);
    }

    @GetMapping("/{source}/chapters")
    public Map<String, Object> chapters(@PathVariable String source,
                                         @RequestParam Long externalComicId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(defaultValue = "asc") String orderDir) {
        if ("niaoniaomh".equals(source)) return ok(chapterPage(niaoniaomhChapterMapper, externalComicId, page, pageSize, orderDir));
        if ("yuyumh".equals(source)) return ok(chapterPage(yuyumhChapterMapper, externalComicId, page, pageSize, orderDir));
        return fail("Unknown source: " + source);
    }

    @GetMapping("/{source}/pages")
    public Map<String, Object> pages(@PathVariable String source,
                                      @RequestParam Long chapterId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        if ("niaoniaomh".equals(source)) return ok(imagePage(niaoniaomhPageMapper, chapterId, page, pageSize));
        if ("yuyumh".equals(source)) return ok(imagePage(yuyumhPageMapper, chapterId, page, pageSize));
        return fail("Unknown source: " + source);
    }

    @GetMapping("/yuyumh/images/{pageId}")
    public ResponseEntity<Resource> yuyumhImage(@PathVariable Long pageId) {
        YuyumhPage page = yuyumhPageMapper.selectById(pageId);
        if (page == null || page.getDownloadStatus() == null || page.getDownloadStatus() != 1) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path image = imageStorage.resolve(page.getLocalPath());
            if (!Files.isRegularFile(image)) return ResponseEntity.notFound().build();
            String contentType = Files.probeContentType(image);
            MediaType mediaType = contentType == null ? MediaType.IMAGE_JPEG : MediaType.parseMediaType(contentType);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                    .body(new FileSystemResource(image));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{source}/crawl-status")
    public Map<String, Object> crawlStatus(@PathVariable String source) {
        try {
            return ok(crawlService.status(source));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{source}/crawl")
    public Map<String, Object> crawl(@PathVariable String source) {
        try {
            return ok(crawlService.start(source));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{source}/refresh-matches")
    public Map<String, Object> refreshMatches(@PathVariable String source) {
        if ("niaoniaomh".equals(source)) {
            matcher.refreshNiaoniaomhMatches();
            return ok(stats(niaoniaomhMapper));
        }
        if ("yuyumh".equals(source)) {
            matcher.refreshYuyumhMatches();
            return ok(stats(yuyumhMapper));
        }
        return fail("未知来源：" + source);
    }

    @GetMapping("/{source}/ingest/status")
    public Map<String, Object> ingestStatus(@PathVariable String source) {
        try {
            return ok(ingestService.status(source));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{source}/ingest/chapters")
    public Map<String, Object> ingestChapters(@PathVariable String source,
                                               @RequestParam(required = false) Long externalComicId) {
        try {
            return ok(ingestService.start(source, "chapters", externalComicId));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{source}/ingest/images")
    public Map<String, Object> ingestImages(@PathVariable String source,
                                             @RequestParam(required = false) Long externalComicId) {
        try {
            return ok(ingestService.start(source, "images", externalComicId));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{source}/ingest/stop")
    public Map<String, Object> stopIngest(@PathVariable String source) {
        try {
            return ok(ingestService.stop(source));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        }
    }

    @GetMapping("/ingest/config")
    public Map<String, Object> ingestConfig() {
        Map<String, Object> config = ingestService.config();
        config.put("ikanmhChapterWorkers", ikanmhProperties.getMaxConcurrentDownloads());
        return ok(config);
    }

    @PostMapping("/ingest/config")
    public Map<String, Object> updateIngestConfig(@RequestParam(required = false) Integer chapterWorkers,
                                                    @RequestParam(required = false) Integer pageWorkers,
                                                    @RequestParam(required = false) Integer ikanmhChapterWorkers) {
        Map<String, Object> config = ingestService.config(chapterWorkers, pageWorkers);
        if (ikanmhChapterWorkers != null) {
            ikanmhProperties.setMaxConcurrentDownloads(Math.max(1, Math.min(ikanmhChapterWorkers, 24)));
        }
        config.put("ikanmhChapterWorkers", ikanmhProperties.getMaxConcurrentDownloads());
        return ok(config);
    }

    @GetMapping("/ikanmh/status")
    public Map<String, Object> ikanmhStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("running", IkanmhImageCrawler.running);
        data.put("chapterWorkers", ikanmhProperties.getMaxConcurrentDownloads());
        data.put("message", IkanmhImageCrawler.running ? "图片 URL 入库运行中" : "空闲");
        return ok(data);
    }

    private <T extends ExternalComic> Map<String, Object> page(String source, BaseMapper<T> mapper, int page, int pageSize,
                                                                 String keyword, Integer same, Integer chapterMin, Integer chapterMax,
                                                                 Integer ingestStatus) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like("name", value).or().like("author", value));
        }
        if (same != null && (same == 0 || same == 1)) wrapper.eq("is_same", same);
        if (chapterMin != null && chapterMin >= 0) wrapper.ge("chapter_count", chapterMin);
        if (chapterMax != null && chapterMax > 0) wrapper.lt("chapter_count", chapterMax);
        applyIngestStatusFilter(wrapper, source, ingestStatus);
        wrapper.orderByDesc("source_update_text").orderByDesc("id");
        Page<T> result = mapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100)), wrapper);
        populateProgress(source, result.getRecords());
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        return data;
    }

    private void populateProgress(String source, List<? extends ExternalComic> comics) {
        if (comics.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (ExternalComic comic : comics) ids.add(comic.getId());
        List<Map<String, Object>> rows = "niaoniaomh".equals(source)
                ? niaoniaomhChapterMapper.batchProgress(ids)
                : yuyumhChapterMapper.batchProgress(ids);
        Map<Long, Map<String, Object>> byComicId = new HashMap<>();
        for (Map<String, Object> row : rows) byComicId.put(number(row.get("externalComicId")).longValue(), row);
        for (ExternalComic comic : comics) {
            Map<String, Object> row = byComicId.get(comic.getId());
            comic.setIngestedChapterCount(number(row == null ? null : row.get("ingestedChapterCount")).intValue());
            comic.setImageDoneChapterCount(number(row == null ? null : row.get("imageDoneChapterCount")).intValue());
            comic.setPendingChapterCount(number(row == null ? null : row.get("pendingChapterCount")).intValue());
            comic.setIngestedImageCount(number(row == null ? null : row.get("ingestedImageCount")).longValue());
            int ingested = comic.getIngestedChapterCount();
            int pending = comic.getPendingChapterCount();
            int completed = comic.getImageDoneChapterCount();
            comic.setIngestStatus(comic.getIsSame() != null && comic.getIsSame() == 1
                    ? 3 : (ingested == 0 ? 0 : (pending == 0 && completed == ingested ? 2 : 1)));
        }
    }

    private <T extends ExternalComic> void applyIngestStatusFilter(QueryWrapper<T> wrapper, String source, Integer ingestStatus) {
        if (ingestStatus == null || ingestStatus < 0 || ingestStatus > 2) return;
        wrapper.eq("is_same", 0);
        String chapterTable = "niaoniaomh".equals(source) ? "niaoniaomh_chapter" : "yuyumh_chapter";
        String comicTable = "niaoniaomh".equals(source) ? "niaoniaomh" : "yuyumh";
        String anyChapter = "SELECT 1 FROM " + chapterTable + " c WHERE c.external_comic_id = " + comicTable + ".id";
        String pendingChapter = "SELECT 1 FROM " + chapterTable + " c WHERE c.external_comic_id = " + comicTable + ".id "
                + "AND c.crawl_status IN (0, -1, 5)";
        if (ingestStatus == 0) {
            wrapper.notExists(anyChapter);
        } else if (ingestStatus == 1) {
            wrapper.exists(anyChapter).exists(pendingChapter);
        } else {
            wrapper.exists(anyChapter).notExists(pendingChapter);
        }
    }

    private Number number(Object value) {
        return value instanceof Number ? (Number) value : 0;
    }

    private <T extends ExternalComicChapter> Map<String, Object> chapterPage(BaseMapper<T> mapper, Long externalComicId,
                                                                                int page, int pageSize, String orderDir) {
        QueryWrapper<T> query = new QueryWrapper<T>().eq("external_comic_id", externalComicId);
        if ("desc".equalsIgnoreCase(orderDir)) query.orderByDesc("chapter_no");
        else query.orderByAsc("chapter_no");
        return pageResult(mapper.selectPage(new Page<T>(normalizePage(page), normalizePageSize(pageSize)), query));
    }

    private <T extends ExternalComicPage> Map<String, Object> imagePage(BaseMapper<T> mapper, Long chapterId,
                                                                          int page, int pageSize) {
        QueryWrapper<T> query = new QueryWrapper<T>().eq("chapter_id", chapterId).orderByAsc("page_no");
        return pageResult(mapper.selectPage(new Page<T>(normalizePage(page), normalizePageSize(pageSize)), query));
    }

    private Map<String, Object> pageResult(Page<?> result) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        return data;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizePageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }

    private <T extends ExternalComic> Map<String, Object> stats(BaseMapper<T> mapper) {
        Map<String, Object> data = new HashMap<>();
        long total = mapper.selectCount(new QueryWrapper<T>());
        long same = mapper.selectCount(new QueryWrapper<T>().eq("is_same", 1));
        data.put("total", total);
        data.put("same", same);
        data.put("different", total - same);
        return data;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", data);
        result.put("message", "success");
        return result;
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 1);
        result.put("message", message);
        return result;
    }
}
