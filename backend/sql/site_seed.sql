USE `man3`;

INSERT INTO site_source (name, url, image_rule, detail_url, content_url, enabled, remark, created_at, updated_at) VALUES
('爱看漫画', 'https://www.ikanmh.top', '将 /uploads/ 替换为 CDN https://cdn.ikanmh.top/', 'https://www.ikanmh.top/book/{bookId}', 'https://www.ikanmh.top/chapter/{chapterId}', 1, '主力源站，更新频率高', NOW(), NOW()),
('漫画DB', 'https://mangadb.example.com', '提取 data-src 属性，追加 ?x-oss-process=image/resize,w_1200', 'https://mangadb.example.com/manga/{id}', 'https://mangadb.example.com/manga/{id}/chapter/{chapterId}', 1, '备用源，图片清晰', NOW(), NOW()),
('动漫之家', 'https://www.dmzj.com', '章节页 img.comic-img 的 src 即原图', 'https://www.dmzj.com/info/{bookId}.html', 'https://www.dmzj.com/view/{bookId}/{chapterId}.shtml', 0, '站点近期反爬加强，已暂停', NOW(), NOW()),
('Kuaikan', 'https://www.kuaikanmanhua.com', 'JSON 接口返回 webp，需解码 base64 path', 'https://www.kuaikanmanhua.com/web/topic/{bookId}', 'https://www.kuaikanmanhua.com/web/comic/{chapterId}', 1, '国漫为主', NOW(), NOW());
