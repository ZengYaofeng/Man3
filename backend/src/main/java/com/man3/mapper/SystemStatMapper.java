package com.man3.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.man3.entity.SystemStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemStatMapper extends BaseMapper<SystemStat> {

    @Update("UPDATE system_stat SET book_count = book_count + #{books}, "
            + "chapter_count = chapter_count + #{chapters}, "
            + "total_image_count = total_image_count + #{images}, "
            + "downloaded_image_count = downloaded_image_count + #{downloadedImages}, "
            + "updated_at = NOW() WHERE id = 1")
    int increment(long books, long chapters, long images, long downloadedImages);

    @Update("UPDATE system_stat SET done_book_count = done_book_count + #{doneDelta}, "
            + "failed_book_count = failed_book_count + #{failedDelta}, updated_at = NOW() WHERE id = 1")
    int adjustBookStatus(long doneDelta, long failedDelta);
}
