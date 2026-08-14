package com.man3.utils.crawler.ikanmh;

/**
 * ikanmh 站点页面结构与爬取状态常量
 */
public final class IkanmhConstants {

    private IkanmhConstants() {
    }

    /** 列表页路径, 拼接 ?page=N */
    public static final String BOOKLIST_PATH = "/booklist";

    /** 漫画详情页前缀 /book/{id} */
    public static final String BOOK_PATH = "/book/";

    /** 章节阅读页前缀 /chapter/{id} */
    public static final String CHAPTER_PATH = "/chapter/";

    /** 每页列表条数(用于判断是否最后一页) */
    public static final int PAGE_SIZE = 28;

    /** 爬取状态: 未爬取(列表已入库, 详情未爬) */
    public static final int STATUS_NOT_CRAWLED = 0;
    /** 爬取状态: 章节已爬取 */
    public static final int STATUS_CHAPTER_DONE = 1;
    /** 爬取状态: 图片已爬取 */
    public static final int STATUS_IMAGE_DONE = 2;
    /** 爬取状态: 全部完成 */
    public static final int STATUS_ALL_DONE = 3;
    /** 爬取状态: 失败 */
    public static final int STATUS_FAILED = -1;
}
