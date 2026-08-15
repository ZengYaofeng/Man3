package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 漫画主表 Mapper
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {

    /**
     * 查找尚未判定为"入库完成"(crawl_status != 3)的漫画主表ID
     * 作为入库盘点的待扫描清单
     */
    @Select("SELECT id FROM book WHERE crawl_status != 3 OR crawl_status IS NULL")
    List<Long> findNotFullyDoneBookIds();
}
