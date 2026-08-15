-- 漫画来源站点表(单独建表脚本, 不影响现有表数据)
USE `man3`;

DROP TABLE IF EXISTS `site_source`;
CREATE TABLE `site_source` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name`         VARCHAR(128) NOT NULL                COMMENT '网站名称',
  `url`          VARCHAR(500) NOT NULL                COMMENT '网址(站点根域名)',
  `image_rule`   TEXT                                COMMENT '图片规则(图片URL解析/替换规则说明)',
  `detail_url`   VARCHAR(500) DEFAULT NULL           COMMENT '漫画详情网址模板(支持{bookId}/{id}占位符)',
  `content_url`  VARCHAR(500) DEFAULT NULL           COMMENT '漫画内容(阅读页)网址模板(支持{chapterId}/{id}占位符)',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '是否启用(1启用 0停用)',
  `remark`       VARCHAR(255) DEFAULT NULL           COMMENT '备注',
  `created_at`   DATETIME     DEFAULT NULL           COMMENT '创建时间',
  `updated_at`   DATETIME     DEFAULT NULL           COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='漫画来源站点表';
