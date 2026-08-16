package com.man3.utils.crawler.external;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared bounded worker pool used by external-source chapter and page crawlers. */
@Component
public class ExternalCrawlExecutor {

    public <T> void execute(List<T> tasks, int workers, AtomicBoolean stopRequested, Task<T> task)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, workers));
        try {
            for (T item : tasks) {
                if (stopRequested.get()) break;
                pool.submit(() -> {
                    if (stopRequested.get()) return;
                    task.run(item);
                });
            }
        } finally {
            pool.shutdown();
            while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                if (stopRequested.get()) pool.shutdownNow();
            }
        }
    }

    @FunctionalInterface
    public interface Task<T> {
        void run(T item);
    }
}
