# Man3 项目记忆文档（AI 持久上下文）

> 用途：每次新对话/重启后，AI 先读此文件即可恢复对项目的理解，无需重新探索。
> 最后更新：2026-08-15（基于 git 历史 4f25cac 之前的所有提交重建）

## 1. 项目是什么

一个**漫画爬虫管理系统**，数据源为 ikanmh（ikanmh.top）。后端爬取漫画列表/详情/章节/图片地址并入库 MySQL，前端提供漫画浏览、爬取进度监控、站点源管理等功能。

## 2. 技术栈

- 后端：`Spring Boot 2.7.18` + `Java 8` + `Maven 3.9` + `MyBatis-Plus 3.5.3.2` + `MySQL`
  - 包名 `com.man3`，主类 `com.man3.Man3Application`
  - 端口 `8080`
  - 数据库：`man3`，`jdbc:mysql://localhost:3306/man3`，账号 `root`/`root`
  - 爬虫需系统代理 `127.0.0.1:10808`（`application.yml` 中 `crawler.proxy-enabled: true`）
- 前端：`React 19` + `TypeScript` + `Vite 7` + `Tailwind CSS` + `shadcn/ui`（组件在 `src/components/ui`）
  - 开发端口 `5173`，`@` 别名指向 `src/`
  - 路由用 `react-router-dom`

## 3. 如何启动

```bash
# 后端（在 backend/ 目录）
mvn spring-boot:run        # 或用 -q 减少噪音

# 前端（在 frontend/ 目录）
npm run dev                # 端口 5173
```

## 4. 目录结构

后端 `backend/src/main/java/com/man3/`：

- `controller/`：`CrawlController`(爬虫触发) / `BookApiController` / `ChapterController` / `SiteApiController`(站点源管理)
- `service/` + `service/impl/`：业务逻辑（BookServiceImpl 最大，15KB）
- `mapper/`：MyBatis-Plus Mapper
- `entity/`：`Book`(主表) / `Chapter`(章节子表) / `BookPage`(图片孙表) / `SiteSource`(站点源)
- `config/`：`IkanmhProperties`(爬取配置) / `MybatisPlusConfig`
- `utils/crawler/ikanmh/`：`IkanmhListCrawler` / `IkanmhDetailCrawler` / `IkanmhImageCrawler` / `IkanmhConstants`(状态常量)

前端 `frontend/src/`：

- `pages/`：`Dashboard` / `ComicList` / `ChapterList` / `SiteList` / `SiteDedup` / `CrawlProgress`
- `components/`：`comic/` `layout/`(`AppLayout`) `site/` `ui/`(shadcn)
- `lib/` `hooks/` `types/` `data/`

## 5. 爬虫三步流程（核心业务）

所有触发接口**异步执行、立即返回**：

1. **列表爬取** `GET /api/crawl/booklist` → `IkanmhListCrawler.crawlAllBooks()` 填充主表 `book`
2. **详情爬取** `GET /api/crawl/detail?limit=-1` → `IkanmhDetailCrawler.crawlAllDetails()` 补全主表并抓章节入 `chapter` 子表（**支持断点续爬**：只爬未爬/失败状态）
3. **图片爬取** `GET /api/crawl/image` 或 `/image/start` → `IkanmhImageCrawler.crawlAllImages()` 爬章节图片地址入 `book_page` 孙表

- `GET /api/crawl/status`：返回详情/图片爬虫进度统计（前端轮询此接口做进度 UI）
- `GET /api/crawl/all`：一二步连跑
- `GET /api/crawl/stop`：发停止指令（当前批次结束才停，不强制中断）
- 爬虫运行状态用静态字段 `IkanmhDetailCrawler.running` / `IkanmhImageCrawler.running` 暴露

## 6. 接口约定（重要坑位）

- **所有 API 统一 `/api` 前缀**。历史坑：前端路由 `/crawl/progress` 曾被 vite 代理转发到后端导致 404，已统一加 `/api`。
- `/api/crawl/*` 返回 `{code, message}` 或直接返回 Map，**无统一 code 包装**，前端直接取 body（历史修复点：`2945282`）。
- `/api/site/*` 返回标准 `{code, message, data}` 包装。`SiteApiController` 已加 `@CrossOrigin`。
- 站点源管理：`/api/site/list`(分页+keyword) / `/api/site/all` / `/api/site/save`(POST, 新增或更新) / `/api/site/{id}/enabled`(切换启用) / `/api/site/{id}`(DELETE)。

## 7. 前端页面职责

| 页面 | 路由 | 说明 |
|------|------|------|
| Dashboard | `/` | 首页概览 |
| ComicList | `/comics` | 漫画列表，**分页增强**：总页数、首页/末页按钮、跳页输入框 |
| ChapterList | `/chapters` | 章节列表 |
| SiteList | `/sites` | 站点源管理（CRUD + 启用切换） |
| SiteDedup | `/dedup` | 全站查重 |
| CrawlProgress | `/crawl/progress` | 实时爬取进度（轮询 `/api/crawl/status`） |

## 8. 昨日（2026-08-14~15）开发脉络（git 顺序）

1. `9f4b6d7` 脚手架搭建
2. `4b66b5f` 后端迁移到 `backend/`，新增漫画列表接口 + 图片爬虫框架
3. `002799b` 详情爬取断点续爬（重爬未爬/失败，跳过已存在章节）
4. `7f4841e` 实时爬取进度页 + 详情逐级断点；触发接口改异步避免阻塞
5. `c3eaf4d` 修复 `/crawl/progress` 路由被代理 404；crawl API 统一 `/api` 前缀
6. `2945282` 修复进度接口：前端直接取 body
7. `42eac72` 漫画列表分页增强（总页数/首页末页/跳页）
8. `b32b741` 图片链接爬虫开发完毕
9. `4f25cac` 站点源管理功能 + 图片爬虫重构（选择器修复、免频图片请求）+ 进度 UI 优化

## 9. 待确认 / 可能的后续方向

- 代理配置（127.0.0.1:10808）是否仍可用；若无需代理需改 `proxy-enabled: false`
- 图片是否要真正下载到本地（当前只爬图片地址入 `book_page`，下载目录 `./downloads` 已配置但未见到下载触发）
- 前端是否有未完成的 TODO（以代码中 TODO 注释为准）
