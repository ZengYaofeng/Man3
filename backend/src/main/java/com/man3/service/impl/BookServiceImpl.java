package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.Book;
import com.man3.mapper.BookMapper;
import com.man3.mapper.BookPageMapper;
import com.man3.mapper.ChapterMapper;
import com.man3.service.BookService;
import com.man3.service.ChapterService;
import com.man3.service.SystemStatService;
import com.man3.utils.ComicNameNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    private final BookPageMapper bookPageMapper;
    private final ChapterMapper chapterMapper;
    private final SystemStatService systemStatService;

    public BookServiceImpl(BookMapper bookMapper, ChapterService chapterService,
                           BookPageMapper bookPageMapper, ChapterMapper chapterMapper,
                           SystemStatService systemStatService) {
        this.bookMapper = bookMapper;
        this.chapterService = chapterService;
        this.bookPageMapper = bookPageMapper;
        this.chapterMapper = chapterMapper;
        this.systemStatService = systemStatService;
    }

    @Override
    public void upsertFromList(Book book) {
        book.setNamePinyin(ComicNameNormalizer.toPinyin(book.getName()));
        Book exist = getBySourceBookId(book.getSourceBookId());
        if (exist == null) {
            book.setCrawlStatus(0);
            book.setCreatedAt(LocalDateTime.now());
            book.setUpdatedAt(LocalDateTime.now());
            bookMapper.insert(book);
            systemStatService.recordInserted(1, 0, 0);
        } else {
            // 列表页数据只覆盖名称/封面/简介/评分, 保留已有详情信息
            exist.setName(book.getName());
            exist.setNamePinyin(book.getNamePinyin());
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
        if (book.getName() != null) {
            book.setNamePinyin(ComicNameNormalizer.toPinyin(book.getName()));
        }
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
        Book current = bookMapper.selectById(id);
        Book book = new Book();
        book.setId(id);
        book.setCrawlStatus(status);
        book.setCrawlTime(LocalDateTime.now());
        bookMapper.updateById(book);
        systemStatService.recordBookStatusChange(current == null ? null : current.getCrawlStatus(), status);
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
                                 Integer ingestStatus, Long chapterMin, Long chapterMax) {
        // 排序字段白名单, 防止非法值导致 SQL 注入(配合下方 switch 使用实体属性, 不拼接字符串)
        // chapter/image 走内存进度排序, 也需在白名单内以免被重置为 created_at
        Set<String> allowedSort = new HashSet<>(Arrays.asList("score", "update_time", "clicks", "created_at", "crawl_time", "id", "chapter", "image", "image_done", "chapterCount"));
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
        // 入库状态筛选(基于漫画主表 crawl_status):
        //  0=未入库(null/0/1)  1=入库中(5 处理中)  2=已入库(2/3 图片已爬取/全部完成)
        if (ingestStatus != null) {
            switch (ingestStatus) {
                case 0:
                    wrapper.and(w -> w.isNull(Book::getCrawlStatus)
                            .or().in(Book::getCrawlStatus, 0, 1));
                    break;
                case 1:
                    wrapper.eq(Book::getCrawlStatus, 5);
                    break;
                case 2:
                    wrapper.in(Book::getCrawlStatus, 2, 3);
                    break;
                default:
                    break;
            }
        }
        if (chapterMin != null && chapterMin >= 0) {
            wrapper.ge(Book::getTotalChapterCount, chapterMin);
        }
        if (chapterMax != null && chapterMax > 0) {
            wrapper.lt(Book::getTotalChapterCount, chapterMax);
        }
        // 章节进度/图片进度/图片入库完成排序无法在 book 表直接 ORDER BY(聚合字段在 chapter 表),
        // 因此这几类排序改为: 先按其它可索引字段分页, 查询后填充聚合字段并在内存重排本页
        boolean sortByProgress = "chapter".equals(sortField) || "image".equals(sortField) || "image_done".equals(sortField);

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
            case "crawl_time":
                wrapper.orderBy(true, asc, Book::getCrawlTime);
                break;
            case "chapter":
            case "image":
            case "image_done":
                // 进度类排序: 先用 id 兜底分页, 内存再按完成度重排
                wrapper.orderBy(true, true, Book::getId);
                break;
            case "chapterCount":
                // 入库进度列: 直接按章节总数(冗余字段)排序
                wrapper.orderBy(true, asc, Book::getTotalChapterCount);
                break;
            default:
                wrapper.orderBy(true, asc, Book::getCreatedAt);
        }

        Page<Book> pageParam = new Page<>(Math.max(page, 1), Math.max(pageSize, 1));
        IPage<Book> resultPage = bookMapper.selectPage(pageParam, wrapper);

        // 填充非持久化字段: downloadedImageCount(已下载图片数)需聚合
        // totalChapterCount / totalImageCount 已是持久化字段, 由 refreshCounters 维护
        List<Book> records = resultPage.getRecords();
        if (!records.isEmpty()) {
            // 聚合每本书的已下载图片数(download_status=1)
            List<Long> bookIds = new ArrayList<>();
            for (Book b : records) {
                bookIds.add(b.getId());
            }
            List<Map<String, Object>> downloadedRows = (List) bookPageMapper.countDownloadedByBookIds(bookIds);
            Map<Long, Long> downloadedMap = new HashMap<>();
            for (Map<String, Object> row : downloadedRows) {
                Long bid = toLong(row.get("bookId"));
                Long dl = toLong(row.get("downloaded"));
                if (bid != null) {
                    downloadedMap.put(bid, dl != null ? dl : 0L);
                }
            }

            // 聚合每本书的章节状态: 待爬章节数 / 图片已爬章节数
            List<Map<String, Object>> chRows = chapterMapper.batchChapterStats(bookIds);
            Map<Long, Long> pendingChMap = new HashMap<>();
            Map<Long, Long> imageDoneMap = new HashMap<>();
            for (Map<String, Object> row : chRows) {
                Long bid = toLong(row.get("bookId"));
                if (bid == null) continue;
                pendingChMap.put(bid, toLong(row.get("pendingCh")) == null ? 0L : toLong(row.get("pendingCh")));
                imageDoneMap.put(bid, toLong(row.get("imageDone")) == null ? 0L : toLong(row.get("imageDone")));
            }

            for (Book b : records) {
                // 章节总数 / 图片总数: 持久化字段
                if (b.getTotalChapterCount() == null) {
                    b.setTotalChapterCount(0L);
                }
                if (b.getTotalImageCount() == null) {
                    b.setTotalImageCount(0L);
                }
                // 兼容旧非持久化字段
                b.setChapterCount(b.getTotalChapterCount());
                // 已下载图片数: 从 book_page.download_status=1 聚合
                Long dl = downloadedMap.get(b.getId());
                b.setDownloadedImageCount(dl != null ? dl : 0L);
                // 章节状态: 待爬 / 图片已爬
                b.setPendingChapterCount(pendingChMap.getOrDefault(b.getId(), 0L));
                b.setImageDoneCount(imageDoneMap.getOrDefault(b.getId(), 0L));
            }

            // 进度类排序: 完成度(已下载/总数)高的排前面, 同分再按 id
            if (sortByProgress) {
                if ("image_done".equals(sortField)) {
                    // 已完成图片入库(无待爬章节且已有图片入库)的漫画优先排前;
                    // 同组内按图片入库完成度(图片已爬章节/总章节)降序, 再按 id
                    records.sort((a, b) -> {
                        int doneA = isImageFullyDone(a) ? 1 : 0;
                        int doneB = isImageFullyDone(b) ? 1 : 0;
                        if (doneA != doneB) {
                            return doneB - doneA; // 完成的排前面
                        }
                        double ra = (double) safe(a.getImageDoneCount()) / safeTotal(a);
                        double rb = (double) safe(b.getImageDoneCount()) / safeTotal(b);
                        int cmp = Double.compare(rb, ra);
                        if (cmp != 0) return asc ? -cmp : cmp;
                        return Long.compare(a.getId(), b.getId());
                    });
                } else {
                    final boolean byImage = "image".equals(sortField);
                    records.sort((a, b) -> {
                        double ra = progressRatio(a, byImage);
                        double rb = progressRatio(b, byImage);
                        int cmp = Double.compare(rb, ra);
                        if (cmp != 0) return asc ? -cmp : cmp;
                        return Long.compare(a.getId(), b.getId());
                    });
                }
            }
        }
        return resultPage;
    }

    @Override
    public Map<String, Object> imageStats() {
        Map<String, Object> map = bookPageMapper.stats();
        long total = map.get("total") == null ? 0L : ((Number) map.get("total")).longValue();
        long downloaded = map.get("downloaded") == null ? 0L : ((Number) map.get("downloaded")).longValue();
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", total);
        result.put("downloaded", downloaded);
        return result;
    }

    @Override
    public Book getById(Long id) {
        return bookMapper.selectById(id);
    }

    /**
     * 更新主表冗余计数字段: 从 chapter / book_page 聚合计算后写回 book
     */
    @Override
    public void refreshCounters(Long bookId) {
        if (bookId == null) {
            return;
        }
        // 聚合章节总数
        Long chapterCount = chapterMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.man3.entity.Chapter>()
                        .eq(com.man3.entity.Chapter::getBookId, bookId));

        // 聚合图片总数(通过 chapter JOIN book_page)
        List<Long> chapterIds = chapterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.man3.entity.Chapter>()
                        .select(com.man3.entity.Chapter::getId)
                        .eq(com.man3.entity.Chapter::getBookId, bookId))
                .stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toList());

        Long imageCount = 0L;
        if (!chapterIds.isEmpty()) {
            imageCount = bookPageMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.man3.entity.BookPage>()
                            .in(com.man3.entity.BookPage::getChapterId, chapterIds));
        }

        Book update = new Book();
        update.setId(bookId);
        update.setTotalChapterCount(chapterCount);
        update.setTotalImageCount(imageCount);
        bookMapper.updateById(update);
    }

    /** 计算某书的进度完成度(已下载/总数), 无总数按0处理 */
    private double progressRatio(Book book, boolean byImage) {
        if (byImage) {
            long total = book.getTotalImageCount() == null ? 0L : book.getTotalImageCount();
            if (total <= 0) {
                return 0.0;
            }
            long done = book.getDownloadedImageCount() == null ? 0L : book.getDownloadedImageCount();
            return (double) done / total;
        }
        // 章节进度: 持久化字段 totalChapterCount
        long total = book.getTotalChapterCount() == null ? 0L : book.getTotalChapterCount();
        return total <= 0 ? 0.0 : 1.0;
    }

    /** 该书是否已完成图片入库: 无待爬章节且已有图片入库的章节 */
    private boolean isImageFullyDone(Book book) {
        long pending = book.getPendingChapterCount() == null ? 0L : book.getPendingChapterCount();
        long imageDone = book.getImageDoneCount() == null ? 0L : book.getImageDoneCount();
        return pending == 0 && imageDone > 0;
    }

    private long safe(Long v) {
        return v == null ? 0L : v;
    }

    private long safeTotal(Book book) {
        long t = book.getTotalChapterCount() == null ? 0L : book.getTotalChapterCount();
        return t <= 0 ? 1L : t;
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
