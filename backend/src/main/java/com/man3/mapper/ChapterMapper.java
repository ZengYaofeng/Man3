package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
        "SUM(CASE WHEN crawl_status = 1 THEN 1 ELSE 0 END) AS imageDone",
        "FROM chapter WHERE book_id IN",
        "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "GROUP BY book_id",
        "</script>"
    })
    List<Map<String, Object>> batchChapterStats(@Param("bookIds") List<Long> bookIds);
}
