package com.man3.service;

import com.man3.utils.crawler.external.NiaoniaomhCrawler;
import com.man3.utils.crawler.external.YuyumhCrawler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 两个外部漫画主表的异步抓取任务和进度状态。 */
@Service
public class ExternalComicCrawlService {

    private final NiaoniaomhCrawler niaoniaomhCrawler;
    private final YuyumhCrawler yuyumhCrawler;
    private final Map<String, CrawlState> states = new HashMap<>();

    public ExternalComicCrawlService(NiaoniaomhCrawler niaoniaomhCrawler, YuyumhCrawler yuyumhCrawler) {
        this.niaoniaomhCrawler = niaoniaomhCrawler;
        this.yuyumhCrawler = yuyumhCrawler;
        states.put("niaoniaomh", new CrawlState());
        states.put("yuyumh", new CrawlState());
    }

    public synchronized Map<String, Object> start(String source) {
        CrawlState state = state(source);
        if (state.running) return state.toMap();
        state.reset();
        CompletableFuture.runAsync(() -> {
            try {
                if ("niaoniaomh".equals(source)) niaoniaomhCrawler.crawlAll(state);
                else yuyumhCrawler.crawlAll(state);
                state.message = "抓取完成，已完成本地漫画库名称对比";
            } catch (Exception e) {
                state.message = "抓取失败：" + e.getMessage();
            } finally {
                state.running = false;
                state.finishedAt = LocalDateTime.now();
            }
        });
        return state.toMap();
    }

    public Map<String, Object> status(String source) {
        return state(source).toMap();
    }

    private CrawlState state(String source) {
        CrawlState state = states.get(source);
        if (state == null) throw new IllegalArgumentException("未知来源：" + source);
        return state;
    }

    public static class CrawlState {
        private volatile boolean running;
        private volatile int processed;
        private volatile int pages;
        private volatile String currentName = "";
        private volatile String message = "尚未开始";
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;

        public synchronized void reset() {
            running = true;
            processed = 0;
            pages = 0;
            currentName = "";
            message = "正在抓取漫画主表和详情元数据";
            startedAt = LocalDateTime.now();
            finishedAt = null;
        }

        public void pageCompleted() { pages++; }
        public void processed(String name) { processed++; currentName = name == null ? "" : name; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("running", running);
            map.put("processed", processed);
            map.put("pages", pages);
            map.put("currentName", currentName);
            map.put("message", message);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            return map;
        }
    }
}
