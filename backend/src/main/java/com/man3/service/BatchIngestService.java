package com.man3.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.Book;
import com.man3.entity.IngestLog;
import com.man3.mapper.BookMapper;
import com.man3.mapper.ChapterMapper;
import com.man3.mapper.IngestLogMapper;
import com.man3.utils.crawler.ikanmh.IkanmhImageCrawler;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 顺序调度待入库漫画，并保存每本漫画的执行审计记录。 */
@Service
public class BatchIngestService {

    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_FAILED = 3;
    private static final int QUERY_CHUNK_SIZE = 500;

    private final BookService bookService;
    private final BookMapper bookMapper;
    private final ChapterService chapterService;
    private final ChapterMapper chapterMapper;
    private final IngestLogMapper ingestLogMapper;
    private final IkanmhImageCrawler imageCrawler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile BatchStatus currentStatus = BatchStatus.idle();

    public BatchIngestService(BookService bookService, BookMapper bookMapper, ChapterService chapterService,
                              ChapterMapper chapterMapper, IngestLogMapper ingestLogMapper,
                              IkanmhImageCrawler imageCrawler) {
        this.bookService = bookService;
        this.bookMapper = bookMapper;
        this.chapterService = chapterService;
        this.chapterMapper = chapterMapper;
        this.ingestLogMapper = ingestLogMapper;
        this.imageCrawler = imageCrawler;
    }

    public BatchPreview preview(String sortField, String sortDir) {
        List<Book> books = listEligibleBooks(sortField, sortDir);
        Map<Long, Integer> chapterCounts = loadChapterCounts(books);
        long chapterCount = 0;
        for (Book book : books) {
            chapterCount += chapterCounts.getOrDefault(book.getId(), 0);
        }
        BatchPreview preview = new BatchPreview();
        preview.bookCount = books.size();
        preview.chapterCount = chapterCount;
        preview.running = running.get();
        return preview;
    }

    public synchronized BatchStatus start(String sortField, String sortDir) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("一键入库任务正在运行");
        }
        if (IkanmhImageCrawler.running) {
            running.set(false);
            throw new IllegalStateException("图片爬虫正在运行，请等待当前任务结束");
        }

        cancelRequested.set(false);
        List<Book> books = listEligibleBooks(sortField, sortDir);
        Map<Long, Integer> chapterCounts = loadChapterCounts(books);
        long chapterCount = 0;
        for (Book book : books) {
            chapterCount += chapterCounts.getOrDefault(book.getId(), 0);
        }

        String batchNo = "ING" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        BatchStatus status = new BatchStatus();
        status.running = !books.isEmpty();
        status.batchNo = batchNo;
        status.totalBooks = books.size();
        status.totalChapters = chapterCount;
        status.message = books.isEmpty() ? "没有待入库或入库中的漫画" : "任务已提交";
        currentStatus = status;

        if (books.isEmpty()) {
            running.set(false);
            return copyStatus(status);
        }

        CompletableFuture.runAsync(() -> runBatch(batchNo, books, chapterCounts, status));
        return copyStatus(status);
    }

    public synchronized BatchStatus cancel() {
        if (!running.get()) {
            throw new IllegalStateException("当前没有正在执行的一键入库任务");
        }
        cancelRequested.set(true);
        currentStatus.cancelRequested = true;
        IkanmhImageCrawler.requestStop();
        return copyStatus(currentStatus);
    }

    public BatchStatus getStatus() {
        return copyStatus(currentStatus);
    }

    public IPage<IngestLog> pageLogs(String batchNo, int page, int pageSize) {
        LambdaQueryWrapper<IngestLog> wrapper = new LambdaQueryWrapper<IngestLog>()
                .eq(batchNo != null && !batchNo.trim().isEmpty(), IngestLog::getBatchNo, batchNo)
                .orderByDesc(IngestLog::getId);
        return ingestLogMapper.selectPage(new Page<IngestLog>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper);
    }

    private void runBatch(String batchNo, List<Book> books, Map<Long, Integer> chapterCounts,
                          BatchStatus status) {
        try {
            for (Book book : books) {
                if (cancelRequested.get()) {
                    break;
                }
                status.currentBookId = book.getId();
                status.currentBookName = book.getName();
                IngestLog log = createLog(batchNo, book, chapterCounts.getOrDefault(book.getId(), 0));
                LocalDateTime startTime = log.getStartTime();
                try {
                    IkanmhImageCrawler.CrawlRunResult result = imageCrawler.crawlImagesForBook(book.getId());
                    Book latest = bookService.getById(book.getId());
                    int pending = chapterService.countPendingImageChapters(book.getId());
                    finishLog(log, latest, result, pending, startTime);
                    if (log.getStatus() == STATUS_DONE) {
                        status.successBooks++;
                    } else {
                        status.failedBooks++;
                    }
                } catch (Exception e) {
                    finishLogWithError(log, bookService.getById(book.getId()), e.getMessage(), startTime);
                    status.failedBooks++;
                }
                status.completedBooks++;
                status.lastCompletedLogId = log.getId();
                status.lastCompletedBookName = log.getBookName();
                status.lastDurationSeconds = log.getDurationSeconds();
                if (cancelRequested.get()) {
                    break;
                }
            }
            if (cancelRequested.get()) {
                status.cancelled = true;
                status.message = "已取消";
            } else {
                status.message = status.failedBooks == 0 ? "全部完成" : "任务完成，存在失败漫画";
            }
        } finally {
            status.running = false;
            status.currentBookId = null;
            status.currentBookName = null;
            running.set(false);
        }
    }

    private IngestLog createLog(String batchNo, Book book, int chapterCount) {
        IngestLog log = new IngestLog();
        LocalDateTime now = LocalDateTime.now();
        log.setBatchNo(batchNo);
        log.setBookId(book.getId());
        log.setBookName(book.getName());
        log.setStatus(STATUS_RUNNING);
        log.setCrawlStatusBefore(book.getCrawlStatus());
        log.setTotalChapterCount(chapterCount);
        log.setPendingChapterCount(chapterService.countPendingImageChapters(book.getId()));
        log.setStartTime(now);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        ingestLogMapper.insert(log);
        return log;
    }

    private void finishLog(IngestLog log, Book latest, IkanmhImageCrawler.CrawlRunResult result,
                           int pending, LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now();
        log.setEndTime(now);
        log.setDurationSeconds(Math.max(0, java.time.Duration.between(startTime, now).getSeconds()));
        log.setSuccessChapterCount(safeInt(result.success));
        log.setFailedChapterCount(safeInt(result.fail));
        log.setCrawlStatusAfter(latest == null ? null : latest.getCrawlStatus());
        log.setImageCount(latest == null || latest.getTotalImageCount() == null ? 0L : latest.getTotalImageCount());
        boolean success = result.started && result.fail == 0 && pending == 0;
        log.setStatus(success ? STATUS_DONE : STATUS_FAILED);
        log.setErrorMessage(success ? null : (result.message == null ? "仍有未完成章节" : result.message));
        log.setUpdatedAt(now);
        ingestLogMapper.updateById(log);
    }

    private void finishLogWithError(IngestLog log, Book latest, String error, LocalDateTime startTime) {
        LocalDateTime now = LocalDateTime.now();
        log.setEndTime(now);
        log.setDurationSeconds(Math.max(0, java.time.Duration.between(startTime, now).getSeconds()));
        log.setStatus(STATUS_FAILED);
        log.setCrawlStatusAfter(latest == null ? null : latest.getCrawlStatus());
        log.setImageCount(latest == null || latest.getTotalImageCount() == null ? 0L : latest.getTotalImageCount());
        log.setErrorMessage(error == null ? "入库任务异常" : error);
        log.setUpdatedAt(now);
        ingestLogMapper.updateById(log);
    }

    private List<Book> listEligibleBooks(String sortField, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        LambdaQueryWrapper<Book> query = new LambdaQueryWrapper<Book>()
                .and(w -> w.isNull(Book::getCrawlStatus).or().in(Book::getCrawlStatus, 0, 1, 5));
        String field = sortField == null ? "crawlTime" : sortField;
        switch (field) {
            case "id":
                query.orderBy(true, asc, Book::getId);
                return bookMapper.selectList(query);
            case "score":
                query.orderBy(true, asc, Book::getScore);
                break;
            case "clicks":
                query.orderBy(true, asc, Book::getClicks);
                break;
            case "updateTime":
                query.orderBy(true, asc, Book::getUpdateTime);
                break;
            case "chapterCount":
                query.orderBy(true, asc, Book::getTotalChapterCount);
                break;
            case "createdAt":
                query.orderBy(true, asc, Book::getCreatedAt);
                break;
            case "crawlTime":
            default:
                query.orderBy(true, asc, Book::getCrawlTime);
                break;
        }
        query.orderByAsc(Book::getId);
        return bookMapper.selectList(query);
    }

    private Map<Long, Integer> loadChapterCounts(List<Book> books) {
        if (books.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> result = new HashMap<Long, Integer>();
        List<Long> ids = new ArrayList<Long>();
        for (Book book : books) {
            ids.add(book.getId());
        }
        for (int i = 0; i < ids.size(); i += QUERY_CHUNK_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(i + QUERY_CHUNK_SIZE, ids.size()));
            for (Map<String, Object> row : chapterMapper.countChaptersByBookIds(chunk)) {
                Number bookId = (Number) row.get("bookId");
                Number chapterCount = (Number) row.get("chapterCount");
                if (bookId != null && chapterCount != null) {
                    result.put(bookId.longValue(), chapterCount.intValue());
                }
            }
        }
        return result;
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static BatchStatus copyStatus(BatchStatus source) {
        BatchStatus copy = new BatchStatus();
        copy.running = source.running;
        copy.batchNo = source.batchNo;
        copy.totalBooks = source.totalBooks;
        copy.totalChapters = source.totalChapters;
        copy.completedBooks = source.completedBooks;
        copy.successBooks = source.successBooks;
        copy.failedBooks = source.failedBooks;
        copy.currentBookId = source.currentBookId;
        copy.currentBookName = source.currentBookName;
        copy.lastCompletedLogId = source.lastCompletedLogId;
        copy.lastCompletedBookName = source.lastCompletedBookName;
        copy.lastDurationSeconds = source.lastDurationSeconds;
        copy.cancelRequested = source.cancelRequested;
        copy.cancelled = source.cancelled;
        copy.message = source.message;
        return copy;
    }

    public static class BatchPreview {
        public int bookCount;
        public long chapterCount;
        public boolean running;
    }

    public static class BatchStatus {
        public volatile boolean running;
        public volatile String batchNo;
        public volatile int totalBooks;
        public volatile long totalChapters;
        public volatile int completedBooks;
        public volatile int successBooks;
        public volatile int failedBooks;
        public volatile Long currentBookId;
        public volatile String currentBookName;
        public volatile Long lastCompletedLogId;
        public volatile String lastCompletedBookName;
        public volatile Long lastDurationSeconds;
        public volatile boolean cancelRequested;
        public volatile boolean cancelled;
        public volatile String message;

        private static BatchStatus idle() {
            BatchStatus status = new BatchStatus();
            status.message = "空闲";
            return status;
        }
    }
}
