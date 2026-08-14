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
     * @param fileSizes 每个图片的字节大小(与 imgUrls 一一对应, 可为 null)
     * @param widths    每个图片的宽度(与 imgUrls 一一对应, 可为 null)
     * @param heights   每个图片的高度(与 imgUrls 一一对应, 可为 null)
     */
    void syncPages(Long chapterId, List<String> imgUrls, List<Long> fileSizes,
                   List<Integer> widths, List<Integer> heights);

    /**
     * 图片总数
     */
    long countAll();
}
