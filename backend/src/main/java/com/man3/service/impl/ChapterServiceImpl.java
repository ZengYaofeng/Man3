package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.Chapter;
import com.man3.mapper.ChapterMapper;
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

    public ChapterServiceImpl(ChapterMapper chapterMapper) {
        this.chapterMapper = chapterMapper;
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
    }

    @Override
    public List<Chapter> listByBookId(Long bookId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getBookId, bookId)
                .orderByAsc(Chapter::getChapterNo));
    }

    @Override
    public long countAll() {
        return chapterMapper.selectCount(null);
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
    public void updateImageResult(Long chapterId, int imageCount, boolean success) {
        Chapter update = new Chapter();
        update.setId(chapterId);
        update.setImageCount(imageCount);
        update.setCrawlStatus(success ? IkanmhConstants.STATUS_IMAGE_DONE : IkanmhConstants.STATUS_FAILED);
        update.setCrawlTime(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(update);
    }
}
