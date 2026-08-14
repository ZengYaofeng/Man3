package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.Book;
import com.man3.mapper.BookMapper;
import com.man3.service.BookService;
import com.man3.service.ChapterService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 漫画主表服务实现
 */
@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;
    private final ChapterService chapterService;

    public BookServiceImpl(BookMapper bookMapper, ChapterService chapterService) {
        this.bookMapper = bookMapper;
        this.chapterService = chapterService;
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
    public long countByCrawlStatus(Integer crawlStatus) {
        return bookMapper.selectCount(
                new LambdaQueryWrapper<Book>().eq(Book::getCrawlStatus, crawlStatus));
    }

    @Override
    public String maxCrawlTime() {
        QueryWrapper<Book> qw = new QueryWrapper<>();
        qw.select("MAX(crawl_time) AS max_crawl_time");
        Map<String, Object> map = bookMapper.selectMaps(qw).stream().findFirst().orElse(null);
        if (map == null) return null;
        Object val = map.get("max_crawl_time");
        return val == null ? null : val.toString();
    }

    @Override
    public IPage<Book> pageQuery(int page, int pageSize, String keyword, String region,
                                 String status, String tag, String sortField, String sortDir,
                                 Integer crawlStatus) {
        // 排序字段白名单, 防止非法值导致 SQL 注入(配合下方 switch 使用实体属性, 不拼接字符串)
        // chapter/image 走内存进度排序, 也需在白名单内以免被重置为 created_at
        Set<String> allowedSort = new HashSet<>(Arrays.asList("score", "update_time", "clicks", "created_at", "id", "chapter", "image"));
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
        if (crawlStatus != null) {
            wrapper.eq(Book::getCrawlStatus, crawlStatus);
        }
        // 章节进度/图片进度排序无法在 book 表直接 ORDER BY(聚合字段在 chapter 表),
        // 因此这两类排序改为: 先按其它可索引字段分页, 查询后填充聚合字段并在内存重排本页
        boolean sortByProgress = "chapter".equals(sortField) || "image".equals(sortField);

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
            case "chapter":
            case "image":
                // 进度类排序: 先用 id 兜底分页, 内存再按完成度重排
                wrapper.orderBy(true, true, Book::getId);
                break;
            default:
                wrapper.orderBy(true, asc, Book::getCreatedAt);
        }

        Page<Book> pageParam = new Page<>(Math.max(page, 1), Math.max(pageSize, 1));
        IPage<Book> resultPage = bookMapper.selectPage(pageParam, wrapper);

        // 批量填充章节进度与图片进度(非持久化字段)
        List<Book> records = resultPage.getRecords();
        if (!records.isEmpty()) {
            List<Long> bookIds = new ArrayList<>();
            for (Book b : records) {
                bookIds.add(b.getId());
            }
            Map<Long, ChapterService.ChapterStats> statsMap = chapterService.batchStats(bookIds);
            for (Book b : records) {
                ChapterService.ChapterStats stats = statsMap.get(b.getId());
                if (stats != null) {
                    b.setChapterCount(stats.totalCh);
                    b.setImageDoneCount(stats.imageDone);
                } else {
                    b.setChapterCount(0L);
                    b.setImageDoneCount(0L);
                }
            }

            // 进度类排序: 完成度(已爬/总数)高的排前面, 同分再按 id
            if (sortByProgress) {
                final boolean byImage = "image".equals(sortField);
                records.sort((a, b) -> {
                    double ra = progressRatio(a, byImage);
                    double rb = progressRatio(b, byImage);
                    // 完成度高的排前面(降序); 升序请求时反转
                    int cmp = Double.compare(rb, ra);
                    if (cmp != 0) return asc ? -cmp : cmp;
                    return Long.compare(a.getId(), b.getId());
                });
            }
        }
        return resultPage;
    }

    @Override
    public Book getById(Long id) {
        return bookMapper.selectById(id);
    }

    /** 计算某书的进度完成度(已爬/总数), 无总数按0处理 */
    private double progressRatio(Book book, boolean byImage) {
        long total = book.getChapterCount() == null ? 0L : book.getChapterCount();
        if (total <= 0) {
            return 0.0;
        }
        long done = byImage
                ? (book.getImageDoneCount() == null ? 0L : book.getImageDoneCount())
                : total; // 章节进度=章节总数即视为满(章节已入库即完成)
        return (double) done / total;
    }
}
