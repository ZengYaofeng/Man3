package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.NiaoniaomhChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NiaoniaomhChapterMapper extends BaseMapper<NiaoniaomhChapter> {

    @Select({
            "<script>",
            "SELECT c.external_comic_id AS externalComicId, COUNT(*) AS ingestedChapterCount,",
            "SUM(CASE WHEN c.crawl_status = 1 THEN 1 ELSE 0 END) AS imageDoneChapterCount,",
            "SUM(CASE WHEN c.crawl_status IN (0, -1, 5) THEN 1 ELSE 0 END) AS pendingChapterCount,",
            "COALESCE(SUM(p.pageCount), 0) AS ingestedImageCount",
            "FROM niaoniaomh_chapter c",
            "LEFT JOIN (SELECT chapter_id, COUNT(*) AS pageCount FROM niaoniaomh_page GROUP BY chapter_id) p ON p.chapter_id = c.id",
            "WHERE c.external_comic_id IN",
            "<foreach collection='comicIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY c.external_comic_id",
            "</script>"
    })
    List<Map<String, Object>> batchProgress(@Param("comicIds") List<Long> comicIds);
}
