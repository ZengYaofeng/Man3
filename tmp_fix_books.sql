-- 基于真实图片 url 就绪情况收敛 book.crawl_status
-- 1) 所有章节图片均就绪的漫画 -> 置为 2(已入库); 原本已是 3(全部完成)保持 3
UPDATE book b
SET b.crawl_status = 2
WHERE b.crawl_status IN (0, 1, 5)
  AND b.id IN (SELECT DISTINCT c.book_id FROM chapter c)
  AND b.id NOT IN (
      SELECT DISTINCT c.book_id FROM chapter c
      LEFT JOIN book_page p ON p.chapter_id = c.id
      WHERE p.img_url IS NULL OR p.img_url = ''
  );

-- 2) 原 2/3/5 但存在图片缺失章节的漫画 -> 回到 1(章节已爬取, 图片未全入)
UPDATE book b
SET b.crawl_status = 1
WHERE b.crawl_status IN (2, 3, 5)
  AND b.id IN (
      SELECT DISTINCT c.book_id FROM chapter c
      LEFT JOIN book_page p ON p.chapter_id = c.id
      WHERE p.img_url IS NULL OR p.img_url = ''
  );
