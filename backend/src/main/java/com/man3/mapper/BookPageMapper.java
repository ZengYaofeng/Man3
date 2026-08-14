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
}
