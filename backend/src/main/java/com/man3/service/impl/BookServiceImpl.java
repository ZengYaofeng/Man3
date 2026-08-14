package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.Book;
import com.man3.mapper.BookMapper;
import com.man3.service.BookService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public List<Book> listByCrawlStatuses(List<Integer> crawlStatuses) {
        if (crawlStatuses == null || crawlStatuses.isEmpty()) {
            return new ArrayList<>();
        }
        return bookMapper.selectList(new LambdaQueryWrapper<Book>()
                .in(Book::getCrawlStatus, crawlStatuses)
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

    @Override
    public IPage<Book> pageQuery(int page, int pageSize, String keyword, String region,
                                 String status, String tag, String sortField, String sortDir) {
        // 排序字段白名单, 防止非法值导致 SQL 注入(配合下方 switch 使用实体属性, 不拼接字符串)
        Set<String> allowedSort = new HashSet<>(Arrays.asList("score", "update_time", "clicks", "created_at", "id"));
        if (!allowedSort.contains(sortField)) {
            sortField = "created_at";
        }
        boolean asc = "asc".equalsIgnoreCase(sortDir);

        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Book::getName, keyword)
                    .or().like(Book::getAlias, keyword)
                    .or().like(Book::getAuthor, keyword));
        }
        if (StringUtils.hasText(region)) {
            wrapper.eq(Book::getRegion, region);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Book::getStatus, status);
        }
        if (StringUtils.hasText(tag)) {
            wrapper.like(Book::getTags, tag);
        }
        // 使用 MP 的 orderBy(已白名单校验 sortField, 防止 SQL 注入)
        // 注意: 不能用 wrapper.last(), 否则会覆盖 MP 自动生成的总记录数 COUNT 查询
        switch (sortField) {
            case "score":
                wrapper.orderBy(true, asc, Book::getScore);
                break;
            case "clicks":
                wrapper.orderBy(true, asc, Book::getClicks);
                break;
            case "update_time":
                wrapper.orderBy(true, asc, Book::getUpdateTime);
                break;
            case "id":
                wrapper.orderBy(true, asc, Book::getId);
                break;
            default:
                wrapper.orderBy(true, asc, Book::getCreatedAt);
        }

        Page<Book> pageParam = new Page<>(Math.max(page, 1), Math.max(pageSize, 1));
        return bookMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public Book getById(Long id) {
        return bookMapper.selectById(id);
    }
}
