package com.man3.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.man3.entity.Book;
import com.man3.entity.InventoryRecord;
import com.man3.entity.InventoryRecordBook;
import com.man3.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 入库盘点服务
 *
 * 盘点逻辑(与需求一致):
 *  1. 查找出未入库漫画主表(mainId): crawl_status != 3
 *  2. 根据 mainId 查找对应的章节(chapterId)
 *  3. 查找图片表(book_page)按 chapterId 是否已完成(实际图片数 == 章节声明图片数)
 *  4. 更新章节入库字段(chapter.crawl_status = 1)
 *  5. 更新漫画入库字段(book.crawl_status = 3) -- 当该漫画所有章节图片均已入库
 *  6. 写盘点记录表 + 盘点明细表
 */
@Service
public class InventoryService {

    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private ChapterMapper chapterMapper;
    @Autowired
    private BookPageMapper bookPageMapper;
    @Autowired
    private InventoryRecordMapper inventoryRecordMapper;
    @Autowired
    private InventoryRecordBookMapper inventoryRecordBookMapper;

    /** 防止并发盘点 */
    private volatile boolean running = false;

    public boolean isRunning() {
        return running;
    }

    /**
     * 执行一次入库盘点(同步, 百万级数据仍较快, 因均为聚合SQL)
     *
     * @return 批次记录
     */
    @Transactional(rollbackFor = Exception.class)
    public InventoryRecord doInventory() {
        if (running) {
            throw new IllegalStateException("盘点正在进行中, 请稍后再试");
        }
        running = true;
        InventoryRecord record = new InventoryRecord();
        String batchNo = "INV" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        record.setBatchNo(batchNo);
        record.setStartTime(LocalDateTime.now());
        record.setStatus(1);
        inventoryRecordMapper.insert(record);

        try {
            // 1. 未入库漫画主表
            List<Long> notDoneIds = bookMapper.findNotFullyDoneBookIds();
            record.setScannedBookCount(notDoneIds.size());

            // 2+4. 更新章节入库字段: 图片已完成的章节置 crawl_status=1
            int updatedChapters = 0;
            if (!notDoneIds.isEmpty()) {
                updatedChapters = chapterMapper.markChaptersImageDone(notDoneIds);
            }
            record.setUpdatedChapterCount(updatedChapters);

            // 3. 重新聚合找出所有图片完成的漫画(全量, 含历史已=3的)
            List<Long> fullyDoneNow = bookPageMapper.findFullyDoneBookIds();
            Set<Long> doneNowSet = new HashSet<>(fullyDoneNow);

            // 盘点前已经是 3 的(用于计算"本次新完成")
            Set<Long> alreadyDone = bookMapper.selectList(
                    new UpdateWrapper<Book>().eq("crawl_status", 3)
            ).stream().map(Book::getId).collect(Collectors.toSet());

            // 5. 批量更新漫画入库字段 crawl_status = 3
            int updatedBooks = 0;
            if (!fullyDoneNow.isEmpty()) {
                UpdateWrapper<Book> uw = new UpdateWrapper<>();
                uw.in("id", fullyDoneNow).set("crawl_status", 3).set("crawl_time", LocalDateTime.now());
                updatedBooks = bookMapper.update(null, uw);
            }
            record.setUpdatedBookCount(updatedBooks);

            // 本次新判定的入库完成漫画 = 现在完成 - 之前已完成
            List<Long> newlyDone = fullyDoneNow.stream()
                    .filter(id -> !alreadyDone.contains(id))
                    .collect(Collectors.toList());
            record.setDoneBookCount(newlyDone.size());

            // 6. 写盘点明细
            if (!newlyDone.isEmpty()) {
                saveDetail(record.getId(), newlyDone);
            }

            record.setEndTime(LocalDateTime.now());
            record.setStatus(2);
            inventoryRecordMapper.updateById(record);
            return record;
        } catch (Exception e) {
            record.setEndTime(LocalDateTime.now());
            record.setStatus(3);
            record.setRemark(e.getMessage());
            inventoryRecordMapper.updateById(record);
            throw e;
        } finally {
            running = false;
        }
    }

    /** 写盘点明细: 批量插入本次新完成漫画 */
    private void saveDetail(Long recordId, List<Long> bookIds) {
        // 章节数
        Map<Long, Integer> chapterCountMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            chapterMapper.countChaptersByBookIds(bookIds).forEach(m ->
                    chapterCountMap.put(((Number) m.get("bookId")).longValue(),
                            ((Number) m.get("chapterCount")).intValue()));
        }
        // 图片数(实际入库)
        Map<Long, Integer> imageCountMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            bookPageMapper.batchImageAgg(bookIds).forEach(m ->
                    imageCountMap.put(((Number) m.get("bookId")).longValue(),
                            ((Number) m.get("actual")).intValue()));
        }
        // 漫画快照(名称/来源ID)
        Map<Long, Book> bookMap = bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));

        List<InventoryRecordBook> details = new ArrayList<>();
        for (Long id : bookIds) {
            Book b = bookMap.get(id);
            InventoryRecordBook d = new InventoryRecordBook();
            d.setRecordId(recordId);
            d.setBookId(id);
            d.setSourceBookId(b != null ? b.getSourceBookId() : null);
            d.setBookName(b != null ? b.getName() : null);
            d.setChapterCount(chapterCountMap.getOrDefault(id, 0));
            d.setImageCount(imageCountMap.getOrDefault(id, 0));
            details.add(d);
        }
        // 分批插入, 防单次过大
        for (int i = 0; i < details.size(); i += 200) {
            List<InventoryRecordBook> sub = details.subList(i, Math.min(i + 200, details.size()));
            sub.forEach(inventoryRecordBookMapper::insert);
        }
    }

    /** 盘点记录列表(按时间倒序) */
    public List<InventoryRecord> listRecords() {
        return inventoryRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InventoryRecord>()
                        .orderByDesc("created_at"));
    }

    /** 某批次的盘点漫画明细 */
    public List<InventoryRecordBook> listRecordBooks(Long recordId) {
        return inventoryRecordBookMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InventoryRecordBook>()
                        .eq("record_id", recordId));
    }
}
