-- ============================================================
-- 入库盘点记录表
-- 记录每次"入库盘点"运行的批次信息, 以及本次盘点新发现的已入库完成漫画数量
-- 库名: Man3  (MySQL 8.0)
-- ============================================================

USE `Man3`;

-- ------------------------------------------------------------
-- 盘点批次记录表 inventory_record
-- 每次点击"入库盘点"生成一条批次记录, 前端盘点记录列表即查询此表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `inventory_record`;
CREATE TABLE `inventory_record` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `batch_no`            VARCHAR(32)  NOT NULL                COMMENT '批次号(如 INV202608151030001)',
  `start_time`          DATETIME     NOT NULL                COMMENT '盘点开始时间',
  `end_time`            DATETIME     DEFAULT NULL            COMMENT '盘点结束时间',
  `status`              TINYINT      NOT NULL DEFAULT 1      COMMENT '状态: 1-进行中 2-已完成 3-失败',
  `scanned_book_count`  INT          NOT NULL DEFAULT 0      COMMENT '扫描的漫画主表数量',
  `done_book_count`     INT          NOT NULL DEFAULT 0      COMMENT '本次新判定为入库完成的漫画数量',
  `updated_chapter_count` INT        NOT NULL DEFAULT 0      COMMENT '本次更新入库字段的章节数量',
  `updated_book_count`  INT          NOT NULL DEFAULT 0      COMMENT '本次更新入库字段的漫画数量',
  `remark`              VARCHAR(512) DEFAULT NULL            COMMENT '备注/错误信息',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库盘点批次记录表';

-- ------------------------------------------------------------
-- 盘点明细表 inventory_record_book
-- 记录每次盘点批次中, 被判定为"入库完成"的漫画明细
-- 前端盘点记录列表"单项下拉"展开后展示的就是这张表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `inventory_record_book`;
CREATE TABLE `inventory_record_book` (
  `id`               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `record_id`        BIGINT   NOT NULL                COMMENT '关联盘点批次 inventory_record.id',
  `book_id`          BIGINT   NOT NULL                COMMENT '漫画主表ID',
  `source_book_id`   VARCHAR(32) DEFAULT NULL         COMMENT '来源站点漫画ID',
  `book_name`        VARCHAR(128) DEFAULT NULL        COMMENT '漫画名称(快照, 便于记录历史)',
  `chapter_count`    INT      NOT NULL DEFAULT 0      COMMENT '该漫画章节总数',
  `image_count`      INT      NOT NULL DEFAULT 0      COMMENT '该漫画图片总数(入库图片张数)',
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_book_id` (`book_id`),
  CONSTRAINT `fk_invrec_book` FOREIGN KEY (`record_id`) REFERENCES `inventory_record` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库盘点批次-漫画明细表';
