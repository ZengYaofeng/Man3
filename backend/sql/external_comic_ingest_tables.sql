CREATE TABLE IF NOT EXISTS `niaoniaomh_chapter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `external_comic_id` BIGINT NOT NULL,
  `source_chapter_id` VARCHAR(128) NOT NULL,
  `chapter_no` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `chapter_url` VARCHAR(1000) NOT NULL,
  `image_count` INT NOT NULL DEFAULT 0,
  `crawl_status` TINYINT NOT NULL DEFAULT 0,
  `crawl_time` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_niaoniaomh_source_chapter` (`source_chapter_id`),
  UNIQUE KEY `uk_niaoniaomh_comic_chapter` (`external_comic_id`, `chapter_no`),
  KEY `idx_niaoniaomh_chapter_status` (`crawl_status`),
  KEY `idx_niaoniaomh_chapter_comic` (`external_comic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `niaoniaomh_page` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `chapter_id` BIGINT NOT NULL,
  `page_no` INT NOT NULL,
  `img_url` VARCHAR(1000) NOT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `img_width` INT DEFAULT NULL,
  `img_height` INT DEFAULT NULL,
  `local_path` VARCHAR(500) DEFAULT NULL,
  `download_status` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_niaoniaomh_chapter_page` (`chapter_id`, `page_no`),
  KEY `idx_niaoniaomh_page_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `yuyumh_chapter` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `external_comic_id` BIGINT NOT NULL,
  `source_chapter_id` VARCHAR(128) NOT NULL,
  `chapter_no` INT NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `chapter_url` VARCHAR(1000) NOT NULL,
  `image_count` INT NOT NULL DEFAULT 0,
  `crawl_status` TINYINT NOT NULL DEFAULT 0,
  `crawl_time` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_yuyumh_source_chapter` (`source_chapter_id`),
  UNIQUE KEY `uk_yuyumh_comic_chapter` (`external_comic_id`, `chapter_no`),
  KEY `idx_yuyumh_chapter_status` (`crawl_status`),
  KEY `idx_yuyumh_chapter_comic` (`external_comic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `yuyumh_page` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `chapter_id` BIGINT NOT NULL,
  `page_no` INT NOT NULL,
  `img_url` VARCHAR(1000) NOT NULL,
  `file_size` BIGINT DEFAULT NULL,
  `img_width` INT DEFAULT NULL,
  `img_height` INT DEFAULT NULL,
  `local_path` VARCHAR(500) DEFAULT NULL,
  `download_status` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_yuyumh_chapter_page` (`chapter_id`, `page_no`),
  KEY `idx_yuyumh_page_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
