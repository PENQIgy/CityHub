-- =====================================================================
--  CityHub 数据库升级脚本
--  在 hmdp 基础数据之上，构建「内容 + 交易」平台所需的表结构
--
--  执行方式：
--    mysql -uroot -p --default-character-set=utf8mb4 cityhub < cityhub_upgrade.sql
--  前置条件：cityhub 库已由 hmdp 库克隆而来（见 migrate-to-cityhub.ps1）
--
--  脚本可重复执行（幂等）：所有 DDL 都先检查 information_schema
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 前置清洗：把历史 NULL 值补上，避免后续 MODIFY ... NOT NULL 失败
-- ---------------------------------------------------------------------
UPDATE `tb_blog`          SET `comments` = 0 WHERE `comments` IS NULL;
UPDATE `tb_blog`          SET `liked`    = 0 WHERE `liked`    IS NULL;
UPDATE `tb_blog_comments` SET `liked`    = 0 WHERE `liked`    IS NULL;
UPDATE `tb_blog_comments` SET `status`   = 0 WHERE `status`   IS NULL;
UPDATE `tb_user_info`     SET `credits`  = 0 WHERE `credits`  IS NULL;
UPDATE `tb_user_info`     SET `fans`     = 0 WHERE `fans`     IS NULL;
UPDATE `tb_user_info`     SET `followee` = 0 WHERE `followee` IS NULL;

DROP PROCEDURE IF EXISTS `cityhub_migrate`;

DELIMITER $$
CREATE PROCEDURE `cityhub_migrate`()
BEGIN
    DECLARE db VARCHAR(64) DEFAULT DATABASE();

    -- =================================================================
    -- 1. 探店笔记 tb_blog 增强
    -- =================================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'view_count') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `view_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览量' AFTER `comments`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'collected') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `collected` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数量' AFTER `view_count`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'score') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `score` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '探店评分 1~5 分乘 10 保存' AFTER `collected`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'topic_id') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `topic_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '话题id' AFTER `score`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'location') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `location` VARCHAR(128) NULL DEFAULT NULL COMMENT '定位/商圈' AFTER `topic_id`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog' AND column_name = 'status') THEN
        ALTER TABLE `tb_blog` ADD COLUMN `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态 0:正常 1:作者隐藏 2:违规下架' AFTER `location`;
    END IF;

    ALTER TABLE `tb_blog` MODIFY COLUMN `shop_id` BIGINT NULL DEFAULT NULL COMMENT '商户id，可为空（纯内容笔记）';
    ALTER TABLE `tb_blog` MODIFY COLUMN `comments` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量';

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog' AND index_name = 'idx_blog_user') THEN
        ALTER TABLE `tb_blog` ADD INDEX `idx_blog_user` (`user_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog' AND index_name = 'idx_blog_shop') THEN
        ALTER TABLE `tb_blog` ADD INDEX `idx_blog_shop` (`shop_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog' AND index_name = 'idx_blog_topic') THEN
        ALTER TABLE `tb_blog` ADD INDEX `idx_blog_topic` (`topic_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog' AND index_name = 'idx_blog_status_time') THEN
        ALTER TABLE `tb_blog` ADD INDEX `idx_blog_status_time` (`status`, `create_time`);
    END IF;

    -- =================================================================
    -- 2. 评论 tb_blog_comments 增强
    -- =================================================================
    ALTER TABLE `tb_blog_comments` MODIFY COLUMN `content` VARCHAR(1024) NOT NULL COMMENT '评论内容';
    ALTER TABLE `tb_blog_comments` MODIFY COLUMN `liked` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数';
    ALTER TABLE `tb_blog_comments` MODIFY COLUMN `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态，0：正常，1：被举报，2：禁止查看';

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_blog_comments' AND column_name = 'images') THEN
        ALTER TABLE `tb_blog_comments` ADD COLUMN `images` VARCHAR(2048) NULL DEFAULT NULL COMMENT '评论图片，多张以","隔开' AFTER `content`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog_comments' AND index_name = 'idx_comment_blog') THEN
        ALTER TABLE `tb_blog_comments` ADD INDEX `idx_comment_blog` (`blog_id`, `parent_id`, `create_time`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_blog_comments' AND index_name = 'idx_comment_user') THEN
        ALTER TABLE `tb_blog_comments` ADD INDEX `idx_comment_user` (`user_id`);
    END IF;

    -- =================================================================
    -- 3. 用户信息 tb_user_info 增强
    -- =================================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_user_info' AND column_name = 'blog_count') THEN
        ALTER TABLE `tb_user_info` ADD COLUMN `blog_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布的笔记数' AFTER `followee`;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_user_info' AND column_name = 'liked_count') THEN
        ALTER TABLE `tb_user_info` ADD COLUMN `liked_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '获赞总数' AFTER `blog_count`;
    END IF;
    ALTER TABLE `tb_user_info` MODIFY COLUMN `credits` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '积分';
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_user_info' AND index_name = 'idx_user_info_fans') THEN
        ALTER TABLE `tb_user_info` ADD INDEX `idx_user_info_fans` (`fans`);
    END IF;

    -- =================================================================
    -- 4. 关注 / 交易 索引补齐
    -- =================================================================
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_follow' AND index_name = 'uk_follow_user_target') THEN
        ALTER TABLE `tb_follow` ADD UNIQUE KEY `uk_follow_user_target` (`user_id`, `follow_user_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_voucher_order' AND index_name = 'idx_order_user') THEN
        ALTER TABLE `tb_voucher_order` ADD INDEX `idx_order_user` (`user_id`, `status`, `create_time`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_voucher_order' AND index_name = 'idx_order_voucher') THEN
        ALTER TABLE `tb_voucher_order` ADD INDEX `idx_order_voucher` (`voucher_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_shop' AND index_name = 'idx_shop_name') THEN
        ALTER TABLE `tb_shop` ADD INDEX `idx_shop_name` (`name`);
    END IF;
END$$
DELIMITER ;

CALL `cityhub_migrate`();
DROP PROCEDURE IF EXISTS `cityhub_migrate`;

-- ---------------------------------------------------------------------
-- 5. 话题 tb_topic
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_topic` (
    `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)      NOT NULL COMMENT '话题名称，不含 #',
    `description` VARCHAR(255)     NULL DEFAULT NULL COMMENT '话题简介',
    `cover`       VARCHAR(512)     NULL DEFAULT NULL COMMENT '话题封面图',
    `blog_count`  INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '关联笔记数',
    `view_count`  INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '话题浏览量',
    `sort`        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态 0:正常 1:下线',
    `create_time` TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_topic_name` (`name`),
    KEY `idx_topic_sort` (`sort`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '话题表';

-- ---------------------------------------------------------------------
-- 6. 笔记收藏 tb_blog_collect
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_blog_collect` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户id',
    `blog_id`     BIGINT UNSIGNED NOT NULL COMMENT '笔记id',
    `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collect_user_blog` (`user_id`, `blog_id`),
    KEY `idx_collect_blog` (`blog_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '笔记收藏表';

-- ---------------------------------------------------------------------
-- 7. 话题种子数据
-- ---------------------------------------------------------------------
INSERT INTO `tb_topic` (`name`, `description`, `cover`, `sort`) VALUES
    ('城市美食',   '本地人也会反复去的馆子',       '/imgs/types/ms.png',  1),
    ('宝藏小店',   '藏在巷子里的高分小店',         '/imgs/types/xc.png',  2),
    ('周末去哪儿', '周末遛弯、展览、公园好去处',   '/imgs/types/jd.png',  3),
    ('咖啡续命',   '手冲、特调、安静的第三空间',   '/imgs/types/ktv.png', 4),
    ('深夜食堂',   '营业到凌晨的深夜好味道',       '/imgs/types/ms.png',  5),
    ('亲子时光',   '带娃友好，省心好去处',         '/imgs/types/yl.png',  6)
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `sort` = VALUES(`sort`);

-- ---------------------------------------------------------------------
-- 8. 历史数据回填
-- ---------------------------------------------------------------------
-- 已有笔记分配话题，保证话题页有内容
UPDATE `tb_blog` b
JOIN (SELECT `id`, ROW_NUMBER() OVER (ORDER BY `id`) AS rn FROM `tb_topic` WHERE `status` = 0) t
    ON t.rn = (b.`id` MOD (SELECT COUNT(*) FROM `tb_topic` WHERE `status` = 0)) + 1
SET b.`topic_id` = t.`id`
WHERE b.`topic_id` IS NULL;

UPDATE `tb_blog` SET `view_count` = `liked` * 7 + 23 WHERE `view_count` = 0;
UPDATE `tb_blog` SET `score` = 38 + (`id` MOD 12) WHERE `score` IS NULL;

UPDATE `tb_topic` t
SET t.`blog_count` = (SELECT COUNT(*) FROM `tb_blog` b WHERE b.`topic_id` = t.`id` AND b.`status` = 0);

-- 为所有已存在用户补齐 user_info
INSERT INTO `tb_user_info` (`user_id`, `city`, `introduce`, `fans`, `followee`, `blog_count`, `liked_count`)
SELECT u.`id`, '杭州', NULL, 0, 0, 0, 0
FROM `tb_user` u
LEFT JOIN `tb_user_info` i ON i.`user_id` = u.`id`
WHERE i.`user_id` IS NULL;

UPDATE `tb_user_info` i
SET i.`followee`   = (SELECT COUNT(*) FROM `tb_follow` f WHERE f.`user_id` = i.`user_id`),
    i.`fans`       = (SELECT COUNT(*) FROM `tb_follow` f WHERE f.`follow_user_id` = i.`user_id`),
    i.`blog_count` = (SELECT COUNT(*) FROM `tb_blog` b WHERE b.`user_id` = i.`user_id` AND b.`status` = 0);
