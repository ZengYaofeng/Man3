package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.Chapter;
import com.man3.entity.Book;
import com.man3.mapper.ChapterMapper;
import com.man3.mapper.BookMapper;
import com.man3.mapper.BookPageMapper;
import com.man3.service.ChapterService;
import com.man3.utils.crawler.ikanmh.IkanmhConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 章节子表服务实现
 */
@Slf4j
@Service
public class ChapterServiceImpl implements ChapterService {

    private final ChapterMapper chapterMapper;
    private final BookMapper bookMapper;
    private final BookPageMapper bookPageMapper;

    public ChapterServiceImpl(ChapterMapper chapterMapper, BookMapper bookMapper,
                               BookPageMapper bookPageMapper) {
        this.chapterMapper = chapterMapper;
        this.bookMapper = bookMapper;
        this.bookPageMapper = bookPageMapper;
    }

    @Override
    public void syncChapters(Long bookId, List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return;
        }
        // 已存在的章节: source_chapter_id -> entity
        Map<String, Chapter> existMap = new HashMap<>();
        chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getBookId, bookId))
                .forEach(c -> existMap.put(c.getSourceChapterId(), c));

        int insert = 0, update = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Chapter chapter : chapters) {
            chapter.setBookId(bookId);
            Chapter exist = existMap.get(chapter.getSourceChapterId());
            if (exist == null) {
                chapter.setId(null);
                chapter.setCrawlStatus(0);
                chapter.setCreatedAt(now);
                chapter.setUpdatedAt(now);
                chapterMapper.insert(chapter);
                insert++;
            } else {
                // 保持主键稳定, 只更新可变字段
                exist.setChapterNo(chapter.getChapterNo());
                exist.setTitle(chapter.getTitle());
                exist.setChapterUrl(chapter.getChapterUrl());
                exist.setUpdatedAt(now);
                chapterMapper.updateById(exist);
                update++;
            }
        }
        log.info("同步章节完成 bookId={}, 新增{}条, 更新{}条", bookId, insert, update);

        // 章节入库后, 更新主表冗余计数字段(章节总数 + 图片总数)
        refreshBookCounters(bookId);
    }

    /**
     * 直接通过 Mapper 聚合计算并更新主表计数(避免循环依赖 BookService)
     */
    private void refreshBookCounters(Long bookId) {
        if (bookId == null) {
            return;
        }
        Long chapterCount = chapterMapper.selectCount(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getBookId, bookId));

        List<Long> chapterIds = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .select(Chapter::getId)
                        .eq(Chapter::getBookId, bookId))
                .stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toList());

        Long imageCount = 0L;
        if (!chapterIds.isEmpty()) {
            imageCount = bookPageMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.man3.entity.BookPage>()
                            .in(com.man3.entity.BookPage::getChapterId, chapterIds));
        }

        Book update = new Book();
        update.setId(bookId);
        update.setTotalChapterCount(chapterCount);
        update.setTotalImageCount(imageCount);
        bookMapper.updateById(update);
    }

    @Override
    public List<Chapter> listByBookId(Long bookId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getBookId, bookId)
                .orderByAsc(Chapter::getChapterNo));
    }

    @Override
    public long countByBookId(Long bookId) {
        return chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getBookId, bookId));
    }

    @Override
    public IPage<Chapter> pageByBookId(Long bookId, int page, int pageSize, String orderDir) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getBookId, bookId);
        if ("desc".equalsIgnoreCase(orderDir)) {
            wrapper.orderByDesc(Chapter::getChapterNo);
        } else {
            wrapper.orderByAsc(Chapter::getChapterNo);
        }
        return chapterMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }

    @Override
    public long countAll() {
        return chapterMapper.selectCount(null);
    }

    @Override
    public long countByImageStatus(int status) {
        return chapterMapper.selectCount(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCrawlStatus, status));
    }

    @Override
    public List<Chapter> listUncrawledImages(int limit) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCrawlStatus, 0)
                .orderByAsc(Chapter::getId);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return chapterMapper.selectList(wrapper);
    }

    @Override
    public List<Chapter> listUncrawledImagesForBook(Long bookId, int limit) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getBookId, bookId)
                .eq(Chapter::getCrawlStatus, 0)
                .orderByAsc(Chapter::getId);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return chapterMapper.selectList(wrapper);
    }

    @Override
    public int countPendingImageChapters(Long bookId) {
        return chapterMapper.countPendingImageChapters(bookId);
    }

    @Override
    public List<Chapter> listPendingImageChaptersForBook(Long bookId, int limit) {
        return chapterMapper.listPendingImageChaptersForBook(bookId, limit);
    }

    @Override
    public void updateImageResult(Long chapterId, int imageCount, boolean success) {
        Chapter update = new Chapter();
        update.setId(chapterId);
        update.setImageCount(imageCount);
        update.setCrawlStatus(success ? IkanmhConstants.STATUS_IMAGE_DONE : IkanmhConstants.STATUS_FAILED);
        update.setCrawlTime(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(update);
    }

    @Override
    public Map<Long, ChapterStats> batchStats(List<Long> bookIds) {
        Map<Long, ChapterStats> result = new HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = chapterMapper.batchChapterStats(bookIds);
        for (Map<String, Object> row : rows) {
            Long bookId = ((Number) row.get("bookId")).longValue();
            long totalCh = row.get("totalCh") == null ? 0L : ((Number) row.get("totalCh")).longValue();
            long imageDone = row.get("imageDone") == null ? 0L : ((Number) row.get("imageDone")).longValue();
            ChapterStats stats = new ChapterStats();
            stats.totalCh = totalCh;
            stats.imageDone = imageDone;
            result.put(bookId, stats);
        }
        return result;
    }

    @Override
    public Map<Long, ImageStats> batchImageStats(List<Long> bookIds) {
        Map<Long, ImageStats> result = new HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = chapterMapper.batchImageStats(bookIds);
        for (Map<String, Object> row : rows) {
            Long bookId = ((Number) row.get("bookId")).longValue();
            long totalImage = row.get("totalImage") == null ? 0L : ((Number) row.get("totalImage")).longValue();
            long declaredImage = row.get("declaredImage") == null ? 0L : ((Number) row.get("declaredImage")).longValue();
            ImageStats stats = new ImageStats();
            stats.totalImage = totalImage;
            stats.declaredImage = declaredImage;
            result.put(bookId, stats);
        }
        return result;
    }

    @Override
    public long sumImageCountCrawled() {
        return chapterMapper.sumImageCountCrawled();
    }

    @Override
    public void markImageProcessing(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Chapter update = new Chapter();
        update.setCrawlStatus(IkanmhConstants.STATUS_IMAGE_PROCESSING);
        chapterMapper.update(update, new LambdaQueryWrapper<Chapter>()
                .in(Chapter::getId, ids));
    }
}
