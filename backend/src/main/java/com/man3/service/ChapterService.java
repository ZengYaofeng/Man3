package com.man3.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
     * 统计某漫画的章节数量(用于断点续爬判断)
     */
    long countByBookId(Long bookId);

    /**
     * 分页查询某漫画的章节
     *
     * @param bookId   漫画ID
     * @param page     页码(从1开始)
     * @param pageSize 每页条数
     * @param orderDir 排序方向 asc(正序)/desc(倒序)
     */
    IPage<Chapter> pageByBookId(Long bookId, int page, int pageSize, String orderDir);

    /**
     * 章节总数
     */
    long countAll();

    /**
     * 按图片爬取状态统计章节数量
     *
     * @param status 图片爬取状态: 0-未爬 2-图片完成 -1-失败
     */
    long countByImageStatus(int status);

    /**
     * 查询图片未爬取的章节(按ID升序, 保证稳定)
     *
     * @param limit 最多返回条数, <=0 表示不限
     */
    List<Chapter> listUncrawledImages(int limit);

    /**
     * 更新章节图片爬取结果
     *
     * @param chapterId  章节ID
     * @param imageCount 图片数量
     * @param success    是否成功(成功置1, 失败置2)
     */
    void updateImageResult(Long chapterId, int imageCount, boolean success);

    /**
     * 批量统计每本漫画的章节进度与图片进度
     *
     * @param bookIds 漫画ID列表
     * @return key=bookId, value={totalCh, imageDone}
     */
    java.util.Map<Long, ChapterStats> batchStats(List<Long> bookIds);

    /** 章节进度聚合结果 */
    class ChapterStats {
        public long totalCh;
        public long imageDone;
    }

    /**
     * 图片进度聚合结果
     */
    class ImageStats {
        public long totalImage;       // 已抓取入库的图片张数(book_page 记录数)
        public long declaredImage;    // 章节声明的图片总数(image_count 之和)
    }

    /**
     * 批量统计每本漫画的图片总数与已入库图片数
     *
     * @param bookIds 漫画ID列表
     * @return key=bookId, value={totalImage, declaredImage}
     */
    java.util.Map<Long, ImageStats> batchImageStats(List<Long> bookIds);

    /** 已爬章节（crawl_status IN (1,2)）的 image_count 之和 */
    long sumImageCountCrawled();

    /**
     * 批量将指定章节标记为"图片处理中"(防止并发工作线程重复领取)
     *
     * @param ids 章节ID列表
     */
    void markImageProcessing(List<Long> ids);
}
