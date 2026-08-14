package com.man3.service;

import java.util.List;

/**
 * 章节图片(孙表)服务
 */
public interface BookPageService {

    /**
     * 同步某章节的图片列表(幂等):
     * 已存在的 page_no 跳过, 不存在的插入, 保证 page_no 连续稳定
     *
     * @param chapterId 章节ID
     * @param imgUrls   图片URL列表(按阅读顺序)
     */
    void syncPages(Long chapterId, List<String> imgUrls);

    /**
     * 图片总数
     */
    long countAll();
}
