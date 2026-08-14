package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.Book;
import com.man3.mapper.BookMapper;
import com.man3.service.BookService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 漫画主表服务实现
 */
@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    public BookServiceImpl(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Override
    public void upsertFromList(Book book) {
        Book exist = getBySourceBookId(book.getSourceBookId());
        if (exist == null) {
            book.setCrawlStatus(0);
            book.setCreatedAt(LocalDateTime.now());
            book.setUpdatedAt(LocalDateTime.now());
            bookMapper.insert(book);
        } else {
            // 列表页数据只覆盖名称/封面/简介/评分, 保留已有详情信息
            exist.setName(book.getName());
            exist.setCoverUrl(book.getCoverUrl());
            exist.setDescription(book.getDescription());
            exist.setScore(book.getScore());
            exist.setSourceUrl(book.getSourceUrl());
            exist.setUpdatedAt(LocalDateTime.now());
            bookMapper.updateById(exist);
        }
    }

    @Override
    public void updateDetail(Book book) {
        book.setUpdatedAt(LocalDateTime.now());
        bookMapper.updateById(book);
    }

    @Override
    public List<Book> listByCrawlStatus(Integer crawlStatus) {
        return bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .eq(Book::getCrawlStatus, crawlStatus)
                .orderByAsc(Book::getId));
    }

    @Override
    public Book getBySourceBookId(String sourceBookId) {
        return bookMapper.selectOne(new LambdaQueryWrapper<Book>()
                .eq(Book::getSourceBookId, sourceBookId));
    }

    @Override
    public void updateCrawlStatus(Long id, Integer status) {
        Book book = new Book();
        book.setId(id);
        book.setCrawlStatus(status);
        book.setCrawlTime(LocalDateTime.now());
        bookMapper.updateById(book);
    }

    @Override
    public long countAll() {
        return bookMapper.selectCount(null);
    }
}
