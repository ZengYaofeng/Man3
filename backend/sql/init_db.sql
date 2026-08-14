-- ============================================================
-- 漫画爬虫数据库初始化脚本
-- 数据库: Man3  (MySQL 8.0)
-- 主表 book      : 漫画基本信息
-- 子表 chapter   : 漫画章节列表
-- 孙表 book_page : 章节图片链接
-- ============================================================

CREATE DATABASE IF NOT EXISTS `Man3`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `Man3`;

-- ------------------------------------------------------------
-- 主表: 漫画基本信息表 book
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_book_id` VARCHAR(32)  NOT NULL                COMMENT '来源站点漫画ID(如1224),用于去重',
  `name`           VARCHAR(128) NOT NULL                COMMENT '漫画名称',
  `alias`          VARCHAR(128) DEFAULT NULL            COMMENT '漫画别名',
  `author`         VARCHAR(255) DEFAULT NULL            COMMENT '作者(多个作者用&或,分隔)',
  `status`         VARCHAR(32)  DEFAULT NULL            COMMENT '连载状态(连载中/已完结)',
  `region`         VARCHAR(64)  DEFAULT NULL            COMMENT '地区/国家',
  `tags`           VARCHAR(255) DEFAULT NULL            COMMENT '标签(多个标签用,分隔)',
  `description`    TEXT                                 COMMENT '漫画简介',
  `cover_url`      VARCHAR(500) DEFAULT NULL            COMMENT '封面图片链接',
  `update_time`    DATETIME     DEFAULT NULL            COMMENT '站点显示的更新时间',
  `clicks`         BIGINT       DEFAULT 0               COMMENT '点击量',
  `score`          DECIMAL(3,1) DEFAULT NULL            COMMENT '评分',
  `source_url`     VARCHAR(500) DEFAULT NULL            COMMENT '漫画主页URL',
  `crawl_status`   TINYINT      NOT NULL DEFAULT 0      COMMENT '爬取状态:0-未爬取 1-章节已爬取 2-图片已爬取 3-全部完成 -1-失败',
  `crawl_time`     DATETIME     DEFAULT NULL            COMMENT '最近爬取时间',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_book_id` (`source_book_id`),
  KEY `idx_status` (`status`),
  KEY `idx_author` (`author`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漫画基本信息表';

-- ------------------------------------------------------------
-- 子表: 漫画章节表 chapter
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `chapter`;
CREATE TABLE `chapter` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id`            BIGINT       NOT NULL                COMMENT '所属漫画ID(关联book.id)',
  `chapter_no`         INT          NOT NULL                COMMENT '章节序号(从1开始,用于排序)',
  `title`              VARCHAR(255) NOT NULL                COMMENT '章节标题',
  `source_chapter_id`  VARCHAR(32)  DEFAULT NULL            COMMENT '来源站点章节ID(如57217)',
  `chapter_url`        VARCHAR(500) DEFAULT NULL            COMMENT '章节阅读页URL',
  `image_count`        INT          DEFAULT 0               COMMENT '本章节图片数量',
  `crawl_status`       TINYINT      NOT NULL DEFAULT 0      COMMENT '爬取状态:0-未爬取 1-图片已爬取 2-失败',
  `crawl_time`         DATETIME     DEFAULT NULL            COMMENT '最近爬取时间',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_chapter` (`book_id`, `chapter_no`),
  UNIQUE KEY `uk_source_chapter_id` (`source_chapter_id`),
  KEY `idx_book_id` (`book_id`),
  CONSTRAINT `fk_chapter_book` FOREIGN KEY (`book_id`) REFERENCES `book` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漫画章节表';

-- ------------------------------------------------------------
-- 孙表: 章节图片链接表 book_page
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `book_page`;
CREATE TABLE `book_page` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chapter_id`       BIGINT        NOT NULL                COMMENT '所属章节ID(关联chapter.id)',
  `page_no`          INT           NOT NULL                COMMENT '图片序号(从1开始,阅读顺序)',
  `img_url`          VARCHAR(1000) NOT NULL                COMMENT '图片URL',
  `file_size`        BIGINT        DEFAULT NULL            COMMENT '图片字节大小',
  `img_width`        INT           DEFAULT NULL            COMMENT '图片宽度(px)',
  `img_height`       INT           DEFAULT NULL            COMMENT '图片高度(px)',
  `local_path`       VARCHAR(500)  DEFAULT NULL            COMMENT '本地下载保存路径(可选)',
  `download_status`  TINYINT       NOT NULL DEFAULT 0      COMMENT '下载状态:0-未下载 1-已下载 2-失败',
  `created_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_page` (`chapter_id`, `page_no`),
  KEY `idx_chapter_id` (`chapter_id`),
  CONSTRAINT `fk_page_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `chapter` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节图片链接表';
