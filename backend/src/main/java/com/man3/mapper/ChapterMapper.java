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
        "SUM(CASE WHEN crawl_status = 1 THEN 1 ELSE 0 END) AS imageDone,",
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
     * 将指定漫画下, 图片已入库完成的章节(实际图片数 == image_count)的 crawl_status 置为 1
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
            "  HAVING COALESCE(ch.image_count, 0) > 0 AND COUNT(p.id) = COALESCE(ch.image_count, 0) " +
            ") done ON done.cid = c.id " +
            "SET c.crawl_status = 1, c.crawl_time = NOW() " +
            "WHERE c.crawl_status != 1" +
            "</script>")
    int markChaptersImageDone(@Param("bookIds") List<Long> bookIds);

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
}
