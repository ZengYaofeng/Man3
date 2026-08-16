package com.man3.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.Book;
import com.man3.entity.SystemStat;
import com.man3.mapper.BookMapper;
import com.man3.mapper.BookPageMapper;
import com.man3.mapper.ChapterMapper;
import com.man3.mapper.SystemStatMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;

/** Dashboard counters. Normal requests read one row; reconciliation is deliberately infrequent. */
@Service
public class SystemStatService {

    private static final long STAT_ID = 1L;
    private final SystemStatMapper statMapper;
    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final BookPageMapper pageMapper;
    private final JdbcTemplate jdbcTemplate;

    public SystemStatService(SystemStatMapper statMapper, BookMapper bookMapper,
                             ChapterMapper chapterMapper, BookPageMapper pageMapper,
                             JdbcTemplate jdbcTemplate) {
        this.statMapper = statMapper;
        this.bookMapper = bookMapper;
        this.chapterMapper = chapterMapper;
        this.pageMapper = pageMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_stat ("
                + "id BIGINT NOT NULL, book_count BIGINT NOT NULL DEFAULT 0, "
                + "chapter_count BIGINT NOT NULL DEFAULT 0, total_image_count BIGINT NOT NULL DEFAULT 0, "
                + "downloaded_image_count BIGINT NOT NULL DEFAULT 0, done_book_count BIGINT NOT NULL DEFAULT 0, "
                + "failed_book_count BIGINT NOT NULL DEFAULT 0, last_reconciled_at DATETIME DEFAULT NULL, "
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        if (statMapper.selectById(STAT_ID) == null) reconcile();
    }

    public SystemStat snapshot() {
        SystemStat stat = statMapper.selectById(STAT_ID);
        if (stat == null) {
            reconcile();
            stat = statMapper.selectById(STAT_ID);
        }
        return stat;
    }

    public void recordInserted(long books, long chapters, long images) {
        if (books != 0 || chapters != 0 || images != 0) statMapper.increment(books, chapters, images, 0);
    }

    public void recordDownloadStatusChange(int oldStatus, int newStatus) {
        if (oldStatus == newStatus) return;
        long delta = (newStatus == 1 ? 1 : 0) - (oldStatus == 1 ? 1 : 0);
        if (delta != 0) statMapper.increment(0, 0, 0, delta);
    }

    public void recordBookStatusChange(Integer oldStatus, Integer newStatus) {
        int before = oldStatus == null ? 0 : oldStatus;
        int after = newStatus == null ? 0 : newStatus;
        if (before == after) return;
        long doneDelta = (after == 3 ? 1 : 0) - (before == 3 ? 1 : 0);
        long failedDelta = (after == -1 ? 1 : 0) - (before == -1 ? 1 : 0);
        if (doneDelta != 0 || failedDelta != 0) statMapper.adjustBookStatus(doneDelta, failedDelta);
    }

    /** Fast repair based on the book-level counters maintained by the ingest flow. */
    public void refreshFromBookCounters() {
        SystemStat current = snapshot();
        SystemStat update = new SystemStat();
        update.setId(STAT_ID);
        update.setBookCount(bookMapper.selectCount(null));
        update.setChapterCount(chapterMapper.selectCount(null));
        update.setTotalImageCount(sumBookImageCounts());
        update.setDownloadedImageCount(current.getDownloadedImageCount());
        update.setDoneBookCount(countByStatus(3));
        update.setFailedBookCount(countByStatus(-1));
        statMapper.updateById(update);
    }

    /** Full scan used after first installation, by an operator, and once per day. */
    public synchronized SystemStat reconcile() {
        Map<String, Object> images = pageMapper.stats();
        SystemStat stat = new SystemStat();
        stat.setId(STAT_ID);
        stat.setBookCount(bookMapper.selectCount(null));
        stat.setChapterCount(chapterMapper.selectCount(null));
        stat.setTotalImageCount(number(images.get("total")));
        stat.setDownloadedImageCount(number(images.get("downloaded")));
        stat.setDoneBookCount(countByStatus(3));
        stat.setFailedBookCount(countByStatus(-1));
        stat.setLastReconciledAt(LocalDateTime.now());
        if (statMapper.selectById(STAT_ID) == null) statMapper.insert(stat);
        else statMapper.updateById(stat);
        return stat;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledReconcile() {
        reconcile();
    }

    private long sumBookImageCounts() {
        Long result = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_image_count), 0) FROM book", Long.class);
        return result == null ? 0L : result;
    }

    private long countByStatus(int status) {
        return bookMapper.selectCount(new LambdaQueryWrapper<Book>().eq(Book::getCrawlStatus, status));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
