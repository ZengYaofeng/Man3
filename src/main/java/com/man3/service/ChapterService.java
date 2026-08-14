package com.man3.service;

import com.man3.entity.Chapter;

import java.util.List;

/**
 * 章节子表服务
 */
public interface ChapterService {

    /**
     * 同步某漫画的章节列表:
     * 新增的插入, 已存在的按 source_chapter_id 更新, 保证章节ID稳定
     */
    void syncChapters(Long bookId, List<Chapter> chapters);

    /**
     * 查询某漫画的全部章节(按章节序号排序)
     */
    List<Chapter> listByBookId(Long bookId);

    /**
     * 章节总数
     */
    long countAll();
}
