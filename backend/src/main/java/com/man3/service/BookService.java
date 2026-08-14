package com.man3.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.entity.Book;

import java.util.List;
import java.util.Map;

/**
 * 漫画主表服务
 */
public interface BookService {

    /**
     * 从列表页数据新增或更新主表(按 source_book_id 去重)
     */
    void upsertFromList(Book book);

    /**
     * 更新详情页抓取的信息
     */
    void updateDetail(Book book);

    /**
     * 按爬取状态查询
     */
    List<Book> listByCrawlStatus(Integer crawlStatus);

    /**
     * 按多个爬取状态查询(用于断点续爬: 同时查询未爬与失败状态)
     */
    List<Book> listByCrawlStatuses(List<Integer> crawlStatuses);

    /**
     * 按来源漫画ID查询
     */
    Book getBySourceBookId(String sourceBookId);

    /**
     * 更新爬取状态与时间
     */
    void updateCrawlStatus(Long id, Integer status);

    /**
     * 主表总数
     */
    long countAll();

    /**
     * 按爬取状态统计数量(用于进度看板)
     */
    long countByCrawlStatus(Integer crawlStatus);

    /**
     * 最近一次爬取时间(任一漫画的 crawl_time 最大值, 用于判断活跃度)
     */
    String maxCrawlTime();

    /**
     * 图片统计(用于仪表盘): { total, downloaded }
     */
    Map<String, Object> imageStats();

    /**
     * 更新漫画主表的冗余计数字段(章节总数 / 图片总数)
     *
     * @param bookId 漫画ID
     */
    void refreshCounters(Long bookId);

    /**
     * 分页 + 多条件查询漫画列表
     *
     * @param page    页码(从1开始)
     * @param pageSize 每页条数
     * @param keyword  关键词(名称/别名/作者)
     * @param region   地区
     * @param status   连载状态
     * @param tag      标签(模糊)
     * @param sortField 排序字段
     * @param sortDir  排序方向 asc/desc
     */
    IPage<Book> pageQuery(int page, int pageSize, String keyword, String region,
                          String status, String tag, String sortField, String sortDir,
                          Integer crawlStatus);

    /**
     * 按主键查询漫画
     */
    Book getById(Long id);
}
