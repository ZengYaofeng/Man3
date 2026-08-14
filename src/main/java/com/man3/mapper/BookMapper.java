package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.Book;
import org.apache.ibatis.annotations.Mapper;

/**
 * 漫画主表 Mapper
 */
@Mapper
public interface BookMapper extends BaseMapper<Book> {
}
