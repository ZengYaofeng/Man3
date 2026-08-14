package com.man3.service;

import com.man3.entity.Book;

import java.util.List;

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
}
