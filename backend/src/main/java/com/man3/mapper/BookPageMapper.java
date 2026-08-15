package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.BookPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 章节图片链接(孙表) Mapper
 */
@Mapper
public interface BookPageMapper extends BaseMapper<BookPage> {

    /**
     * 统计图片总量与已下载量(本地下载完成)
     * download_status: 0-未下载 1-已下载 2-失败
     *
     * @return { total, downloaded }
     */
    @Select({
        "SELECT COUNT(*) AS total,",
        "SUM(CASE WHEN download_status = 1 THEN 1 ELSE 0 END) AS downloaded",
        "FROM book_page"
    })
    Map<String, Object> stats();

    /**
     * 按漫画ID批量统计已下载图片数(download_status=1)
     *
     * @param bookIds 漫画ID列表
     * @return bookId -> 已下载图片数
     */
    @Select({
        "<script>",
        "SELECT c.book_id AS bookId, COUNT(*) AS downloaded",
        "FROM book_page p",
        "JOIN chapter c ON p.chapter_id = c.id",
        "WHERE p.download_status = 1",
        "AND c.book_id IN",
        "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "GROUP BY c.book_id",
        "</script>"
    })
    List<Map<String, Object>> countDownloadedByBookIds(@Param("bookIds") List<Long> bookIds);

    /**
     * 找出所有章节图片均已入库完成的漫画ID
     * 判定口径(与代码 isImageFullyDone 一致):
     *   该漫画每个章节的 book_page 实际图片数 == chapter.image_count, 且声明图片总数 > 0
     * 性能: 直接聚合, 避免逐本循环
     *
     * @return 已入库完成漫画的 book_id 列表
     */
    @Select("SELECT ca.book_id AS bookId " +
            "FROM ( " +
            "  SELECT c.book_id, " +
            "         SUM(c.image_count) AS declared, " +
            "         SUM(COALESCE(pg.cnt, 0)) AS actual " +
            "  FROM chapter c " +
            "  LEFT JOIN (SELECT chapter_id, COUNT(*) AS cnt FROM book_page GROUP BY chapter_id) pg " +
            "    ON pg.chapter_id = c.id " +
            "  GROUP BY c.book_id " +
            ") ca " +
            "WHERE ca.declared > 0 AND ca.declared = ca.actual")
    List<Long> findFullyDoneBookIds();

    /**
     * 批量统计每个漫画的: 声明图片总数(章节 image_count 之和) 与 实际入库图片数(book_page 行数)
     *
     * @param bookIds 漫画ID列表
     * @return bookId -> { declared, actual }
     */
    @Select("<script>" +
            "SELECT ca.book_id AS bookId, " +
            "COALESCE(SUM(ca.declared), 0) AS declared, " +
            "COALESCE(SUM(ca.actual), 0) AS actual " +
            "FROM ( " +
            "  SELECT c.book_id, " +
            "         c.image_count AS declared, " +
            "         COALESCE(pg.cnt, 0) AS actual " +
            "  FROM chapter c " +
            "  LEFT JOIN (SELECT chapter_id, COUNT(*) AS cnt FROM book_page GROUP BY chapter_id) pg " +
            "    ON pg.chapter_id = c.id " +
            "  WHERE c.book_id IN " +
            "  <foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            ") ca " +
            "GROUP BY ca.book_id" +
            "</script>")
    List<Map<String, Object>> batchImageAgg(@Param("bookIds") List<Long> bookIds);
}
