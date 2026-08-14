package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.man3.entity.BookPage;
import com.man3.mapper.BookPageMapper;
import com.man3.service.BookPageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 章节图片(孙表)服务实现
 */
@Slf4j
@Service
public class BookPageServiceImpl implements BookPageService {

    private final BookPageMapper bookPageMapper;

    public BookPageServiceImpl(BookPageMapper bookPageMapper) {
        this.bookPageMapper = bookPageMapper;
    }

    @Override
    public void syncPages(Long chapterId, List<String> imgUrls, List<Long> fileSizes,
                          List<Integer> widths, List<Integer> heights) {
        if (chapterId == null || imgUrls == null || imgUrls.isEmpty()) {
            return;
        }
        // 已存在的 page_no, 跳过避免重复
        Set<Integer> existPages = new HashSet<>();
        bookPageMapper.selectList(new LambdaQueryWrapper<BookPage>()
                        .eq(BookPage::getChapterId, chapterId))
                .forEach(p -> existPages.add(p.getPageNo()));

        LocalDateTime now = LocalDateTime.now();
        int insert = 0;
        for (int i = 0; i < imgUrls.size(); i++) {
            int pageNo = i + 1;
            if (existPages.contains(pageNo)) {
                continue;
            }
            BookPage page = new BookPage();
            page.setChapterId(chapterId);
            page.setPageNo(pageNo);
            page.setImgUrl(imgUrls.get(i));
            if (fileSizes != null && i < fileSizes.size()) {
                page.setFileSize(fileSizes.get(i));
            }
            if (widths != null && i < widths.size()) {
                page.setImgWidth(widths.get(i));
            }
            if (heights != null && i < heights.size()) {
                page.setImgHeight(heights.get(i));
            }
            page.setDownloadStatus(0);
            page.setCreatedAt(now);
            page.setUpdatedAt(now);
            try {
                bookPageMapper.insert(page);
                insert++;
            } catch (DuplicateKeyException e) {
                // 并发重复写入时忽略
                log.debug("图片 page_no={} 已存在, 跳过 chapterId={}", pageNo, chapterId);
            }
        }
        log.info("同步图片完成 chapterId={}, 新增{}条, 共{}页", chapterId, insert, imgUrls.size());
    }

    @Override
    public long countAll() {
        return bookPageMapper.selectCount(null);
    }
}
