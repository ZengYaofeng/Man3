-- 全局修复章节 crawl_status 脏数据, 完全以"真实图片 url 是否就绪"为准
-- 1) 图片已真实入库(存在 book_page 且所有 img_url 非空, 数量=image_count)的章节 -> 1
UPDATE chapter c
JOIN (
  SELECT ch.id AS cid
  FROM chapter ch
  LEFT JOIN book_page p ON p.chapter_id = ch.id
  GROUP BY ch.id, ch.image_count
  HAVING COUNT(p.id) > 0
    AND (COALESCE(ch.image_count, 0) <= 0 OR COUNT(p.id) = COALESCE(ch.image_count, 0))
    AND MIN(p.img_url) IS NOT NULL AND MAX(p.img_url) IS NOT NULL
    AND MIN(p.img_url) <> '' AND MAX(p.img_url) <> ''
) ready ON ready.cid = c.id
SET c.crawl_status = 1, c.crawl_time = NOW();

-- 2) 图片缺失(存在空 url)或根本无 book_page 的章节 -> 0(待爬, 覆盖 5 残留)
UPDATE chapter c
SET c.crawl_status = 0, c.crawl_time = NULL
WHERE c.crawl_status = 5
AND (
  NOT EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id)
  OR EXISTS (SELECT 1 FROM book_page p WHERE p.chapter_id = c.id AND (p.img_url IS NULL OR p.img_url = ''))
);
