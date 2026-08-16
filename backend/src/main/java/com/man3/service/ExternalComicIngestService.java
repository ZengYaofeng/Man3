package com.man3.service;

import com.man3.utils.crawler.external.ExternalComicIngestCrawler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates cancellable chapter/page ingestion for external comic sources. */
@Service
public class ExternalComicIngestService {

    private final Map<String, ExternalComicIngestCrawler> crawlers = new HashMap<>();
    private final Map<String, IngestState> states = new HashMap<>();
    private volatile int chapterWorkers = 4;
    private volatile int pageWorkers = 12;

    public ExternalComicIngestService(List<ExternalComicIngestCrawler> crawlerList) {
        for (ExternalComicIngestCrawler crawler : crawlerList) {
            crawlers.put(crawler.source(), crawler);
            states.put(crawler.source(), new IngestState());
        }
    }

    public synchronized Map<String, Object> start(String source, String stage, Long externalComicId) {
        ExternalComicIngestCrawler crawler = crawler(source);
        IngestState state = state(source);
        if (state.running) return state.toMap();
        state.start(stage);
        int workers = "chapters".equals(stage) ? chapterWorkers : pageWorkers;
        CompletableFuture.runAsync(() -> {
            try {
                if ("chapters".equals(stage)) crawler.crawlChapters(state, workers, externalComicId);
                else crawler.crawlPages(state, workers, externalComicId);
                if (!state.stopRequested.get()) state.message = "任务完成";
            } catch (Exception e) {
                state.message = "任务失败: " + e.getMessage();
            } finally {
                state.running = false;
                state.finishedAt = LocalDateTime.now();
            }
        });
        return state.toMap();
    }

    public synchronized Map<String, Object> start(String source, String stage) {
        return start(source, stage, null);
    }

    public synchronized Map<String, Object> stop(String source) {
        IngestState state = state(source);
        state.stopRequested.set(true);
        state.message = "已请求停止，正在结束已开始的请求";
        return state.toMap();
    }

    public Map<String, Object> status(String source) {
        return state(source).toMap();
    }

    public synchronized Map<String, Object> config(Integer chapterWorkers, Integer pageWorkers) {
        if (chapterWorkers != null) this.chapterWorkers = clamp(chapterWorkers);
        if (pageWorkers != null) this.pageWorkers = clamp(pageWorkers);
        Map<String, Object> result = new HashMap<>();
        result.put("chapterWorkers", this.chapterWorkers);
        result.put("pageWorkers", this.pageWorkers);
        return result;
    }

    public Map<String, Object> config() {
        return config(null, null);
    }

    private int clamp(int workers) {
        return Math.max(1, Math.min(workers, 24));
    }

    private ExternalComicIngestCrawler crawler(String source) {
        ExternalComicIngestCrawler crawler = crawlers.get(source);
        if (crawler == null) throw new IllegalArgumentException("Unknown source: " + source);
        return crawler;
    }

    private IngestState state(String source) {
        IngestState state = states.get(source);
        if (state == null) throw new IllegalArgumentException("Unknown source: " + source);
        return state;
    }

    public static class IngestState {
        private volatile boolean running;
        private volatile String stage = "";
        private volatile int total;
        private volatile int processed;
        private volatile int success;
        private volatile int failed;
        private volatile String currentName = "";
        private volatile String message = "尚未开始";
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);

        synchronized void start(String stage) {
            this.running = true;
            this.stage = stage;
            this.total = 0;
            this.processed = 0;
            this.success = 0;
            this.failed = 0;
            this.currentName = "";
            this.message = "正在入库";
            this.startedAt = LocalDateTime.now();
            this.finishedAt = null;
            this.stopRequested.set(false);
        }

        public synchronized void total(int total) { this.total = total; }
        public synchronized void success(String name) { processed++; success++; currentName = name == null ? "" : name; }
        public synchronized void failed(String name) { processed++; failed++; currentName = name == null ? "" : name; }
        public boolean shouldStop() { return stopRequested.get(); }
        public AtomicBoolean stopToken() { return stopRequested; }

        public synchronized Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("running", running);
            map.put("stage", stage);
            map.put("total", total);
            map.put("processed", processed);
            map.put("success", success);
            map.put("failed", failed);
            map.put("currentName", currentName);
            map.put("message", message);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            return map;
        }
    }
}
