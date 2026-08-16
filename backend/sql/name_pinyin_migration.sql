-- Name comparison key for simplified/traditional Chinese and pinyin matching.
ALTER TABLE `book`
  ADD COLUMN `name_pinyin` VARCHAR(512) DEFAULT NULL COMMENT '漫画名称拼音匹配键' AFTER `name`;
ALTER TABLE `book`
  ADD INDEX `idx_book_name_pinyin` (`name_pinyin`);

ALTER TABLE `niaoniaomh`
  ADD COLUMN `name_pinyin` VARCHAR(512) DEFAULT NULL COMMENT '漫画名称拼音匹配键' AFTER `name`;
ALTER TABLE `niaoniaomh`
  ADD INDEX `idx_niaoniaomh_name_pinyin` (`name_pinyin`);

ALTER TABLE `yuyumh`
  ADD COLUMN `name_pinyin` VARCHAR(512) DEFAULT NULL COMMENT '漫画名称拼音匹配键' AFTER `name`;
ALTER TABLE `yuyumh`
  ADD INDEX `idx_yuyumh_name_pinyin` (`name_pinyin`);
