package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 章节子表 Mapper
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
}
