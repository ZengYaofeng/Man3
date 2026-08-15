package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 章节子表 Mapper
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {

    /**
     * 批量统计每个漫画的章节总数与图片已爬章节数
     *
     * @param bookIds 漫画ID列表
     * @return 每行: book_id, total_ch, image_done
     */
    @Select({
        "<script>",
        "SELECT book_id AS bookId, COUNT(*) AS totalCh,",
        "SUM(CASE WHEN crawl_status IN (1, 2) THEN 1 ELSE 0 END) AS imageDone,",
        "SUM(CASE WHEN crawl_status = 0 THEN 1 ELSE 0 END) AS pendingCh",
        "FROM chapter WHERE book_id IN",
        "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "GROUP BY book_id",
        "</script>"
    })
    List<Map<String, Object>> batchChapterStats(@Param("bookIds") List<Long> bookIds);

    /**
     * 批量统计每个漫画的图片总数与已入库图片数
     * book_page(孙表) -> chapter(子表) -> book(主表)
     *
     * @param bookIds 漫画ID列表
     * @return 每行: book_id, total_image(已抓取入库图片数), declared_image(章节声明图片总数)
     */
    @Select({
        "<script>",
        "SELECT c.book_id AS bookId, COUNT(p.id) AS totalImage,",
        "SUM(COALESCE(c.image_count, 0)) AS declaredImage",
        "FROM book_page p JOIN chapter c ON p.chapter_id = c.id",
        "WHERE c.book_id IN",
        "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "GROUP BY c.book_id",
        "</script>"
    })
    List<Map<String, Object>> batchImageStats(@Param("bookIds") List<Long> bookIds);

    /**
     * 统计已爬章节（crawl_status IN (1,2)）的 image_count 之和
     */
    @Select("SELECT COALESCE(SUM(image_count), 0) FROM chapter WHERE crawl_status IN (1, 2)")
    long sumImageCountCrawled();

    /**
     * 将指定漫画下, 图片已真实入库完成的章节(所有 book_page 的 img_url 均非空, 且数量==image_count)的 crawl_status 置为 1
     * 仅更新当前不是 1 的章节, 返回受影响行数
     *
     * @param bookIds 漫画ID列表
     * @return 更新行数(被标记为图片已爬取的章节数)
     */
    @Update("<script>" +
            "UPDATE chapter c " +
            "JOIN ( " +
            "  SELECT ch.id AS cid " +
            "  FROM chapter ch " +
            "  LEFT JOIN book_page p ON p.chapter_id = ch.id " +
            "  WHERE ch.book_id IN " +
            "  <foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "  GROUP BY ch.id, ch.image_count " +
            "  HAVING COALESCE(ch.image_count, 0) &gt; 0 " +
            "    AND COUNT(p.id) = COALESCE(ch.image_count, 0) " +
            "    AND MIN(p.img_url) IS NOT NULL AND MAX(p.img_url) IS NOT NULL " +
            "    AND MIN(p.img_url) &lt;&gt; '' AND MAX(p.img_url) &lt;&gt; '' " +
            ") done ON done.cid = c.id " +
            "SET c.crawl_status = 1, c.crawl_time = NOW() " +
            "WHERE c.crawl_status != 1" +
            "</script>")
    int markChaptersImageDone(@Param("bookIds") List<Long> bookIds);

    /**
     * 将指定漫画下, 被标记为"处理中"(5)但实际图片并未真实入库完成的章节, 重置回"未爬取"(0),
     * 避免中断残留的 5 状态导致漏爬/误判已入库. 真实就绪的章节会被 markChaptersImageDone 置为 1.
     *
     * @param bookIds 漫画ID列表
     */
    @Update("<script>" +
            "UPDATE chapter c " +
            "SET c.crawl_status = 0, c.crawl_time = NULL " +
            "WHERE c.crawl_status = 5 " +
            "AND c.book_id IN " +
            "  <foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND (NOT EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id) " +
            "     OR EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id " +
            "                AND (p.img_url IS NULL OR p.img_url = '')))" +
            "</script>")
    int resetStuckProcessingChapters(@Param("bookIds") List<Long> bookIds);

    /**
     * 批量统计每个漫画的章节总数(用于盘点明细)
     *
     * @param bookIds 漫画ID列表
     * @return bookId -> chapterCount
     */
    @Select("<script>" +
            "SELECT book_id AS bookId, COUNT(*) AS chapterCount " +
            "FROM chapter WHERE book_id IN " +
            "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY book_id" +
            "</script>")
    List<Map<String, Object>> countChaptersByBookIds(@Param("bookIds") List<Long> bookIds);

    /**
     * 统计指定漫画中"图片已真实入库"的章节数:
     * 章节关联的所有 book_page 的 img_url 均非空(即不存在 url 缺失的图片)
     *
     * @param bookId 漫画ID
     * @return 已入库章节数
     */
    @Select("SELECT COUNT(DISTINCT c.id) FROM chapter c " +
            "WHERE c.book_id = #{bookId} " +
            "AND EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id) " +
            "AND NOT EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id " +
            "AND (p.img_url IS NULL OR p.img_url = ''))")
    int countImageDoneChapters(@Param("bookId") Long bookId);

    /**
     * 统计指定漫画中"待爬(图片未真实入库)"的章节数:
     * 章节没有关联的 book_page, 或存在 img_url 缺失(空/NULL)的图片
     *
     * @param bookId 漫画ID
     * @return 待爬章节数
     */
    @Select("SELECT COUNT(DISTINCT c.id) FROM chapter c " +
            "WHERE c.book_id = #{bookId} " +
            "AND (NOT EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id) " +
            "OR EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id " +
            "AND (p.img_url IS NULL OR p.img_url = '')))")
    int countPendingImageChapters(@Param("bookId") Long bookId);

    /**
     * 查询指定漫画下"待爬(图片未真实入库)"的章节列表(按ID升序, 保证稳定领取顺序)
     * 判定标准: 章节没有 book_page, 或存在 img_url 缺失的图片
     *
     * @param bookId 漫画ID
     * @param limit  最多返回条数, <=0 表示不限
     */
    @Select({
        "<script>",
        "SELECT c.* FROM chapter c",
        "WHERE c.book_id = #{bookId} ",
        "AND (NOT EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id)",
        "OR EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id AND (p.img_url IS NULL OR p.img_url = '')))",
        "ORDER BY c.id ASC",
        "<if test='limit != null and limit > 0'> LIMIT #{limit} </if>",
        "</script>"
    })
    List<Chapter> listPendingImageChaptersForBook(@Param("bookId") Long bookId, @Param("limit") int limit);
}
