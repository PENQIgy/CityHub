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
    -- 秒杀券表的时间列允许 NULL：普通券（type = 0）不限时，用 NULL 表示「不限结束时间」。
    -- 老库里这两列是 NOT NULL DEFAULT CURRENT_TIMESTAMP，新建普通券时会被填成
    -- begin_time == end_time 的「零宽时间窗」，导致券一下单就被判成「已结束」。
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = db AND table_name = 'tb_seckill_voucher' AND column_name = 'end_time' AND is_nullable = 'NO') THEN
        ALTER TABLE `tb_seckill_voucher`
            MODIFY COLUMN `begin_time` TIMESTAMP NULL DEFAULT NULL COMMENT '生效时间，秒杀券必填',
            MODIFY COLUMN `end_time`   TIMESTAMP NULL DEFAULT NULL COMMENT '失效时间，秒杀券必填；NULL = 不限时（普通券）';
    END IF;

    -- 把普通券（type = 0）身上遗留的时间窗清掉，它们没有业务含义
    UPDATE `tb_seckill_voucher` sv
    JOIN `tb_voucher` v ON v.`id` = sv.`voucher_id`
    SET sv.`begin_time` = NULL, sv.`end_time` = NULL
    WHERE v.`type` = 0 AND (sv.`begin_time` IS NOT NULL OR sv.`end_time` IS NOT NULL);

    -- 普通券的「不限量」库存标记统一成 0。
    -- 不能是 -1：取消订单会执行 stock = stock + 1，-1 会被加回 0，而 0 是「限量且售罄」的取值，
    -- 会让不限量券被误判成售罄，取消后重新下单直接报「库存不足」。
    UPDATE `tb_seckill_voucher` sv
    JOIN `tb_voucher` v ON v.`id` = sv.`voucher_id`
    SET sv.`stock` = 0
    WHERE v.`type` = 0 AND (sv.`stock` IS NULL OR sv.`stock` <> 0);

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_follow' AND index_name = 'uk_follow_user_target') THEN
        ALTER TABLE `tb_follow` ADD UNIQUE KEY `uk_follow_user_target` (`user_id`, `follow_user_id`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_voucher_order' AND index_name = 'idx_order_user') THEN
        ALTER TABLE `tb_voucher_order` ADD INDEX `idx_order_user` (`user_id`, `status`, `create_time`);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_voucher_order' AND index_name = 'idx_order_voucher') THEN
        ALTER TABLE `tb_voucher_order` ADD INDEX `idx_order_voucher` (`voucher_id`);
    END IF;

    -- 清理历史重复订单：一人一单之前，同一用户对同一张券可能落下多条订单，
    -- 先按 (user_id, voucher_id) 分组只保留一条，否则下面的唯一索引无法创建。
    -- 保留规则：status 最小（1 未支付 < 2 已支付 < 3 已核销 < 4 已取消），status 相同时取 id 最小。
    -- 用派生表先算出每组要保留的 id，避免 tb_voucher_order 自连接在大表上退化成 O(n^2)。
    DELETE FROM `tb_voucher_order`
    WHERE `id` NOT IN (
        SELECT `keep_id` FROM (
            SELECT SUBSTRING_INDEX(
                       GROUP_CONCAT(`id` ORDER BY `status` ASC, `id` ASC), ',', 1) AS `keep_id`
            FROM `tb_voucher_order`
            GROUP BY `user_id`, `voucher_id`
        ) k
    );

    -- 唯一索引是一人一单的最后一道防线：应用层已经做了拦截，这里再加一层数据库约束，
    -- 保证并发/异常情况下同一个用户在同一张券上也不会产生第二条订单。
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = db AND table_name = 'tb_voucher_order' AND index_name = 'uk_order_user_voucher') THEN
        ALTER TABLE `tb_voucher_order` ADD UNIQUE KEY `uk_order_user_voucher` (`user_id`, `voucher_id`);
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
-- 8. 一人一单说明
-- ---------------------------------------------------------------------
-- `tb_voucher_order` 上 (user_id, voucher_id) 唯一（见 uk_order_user_voucher），
-- 即「一个用户对同一张代金券最多一条订单」。
-- 注意：已取消(4) 的订单依然占位——它同样占用了这个唯一键，用户重新下单时不会
-- 插入新行，而是复用原来那行订单（由应用层 `reactivateOrder` 处理：重置状态与时间）。
-- 因此「先取消再购买」不会撞唯一键，历史取消记录也不会被自动清理。

-- ---------------------------------------------------------------------
-- 9. 历史数据回填
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
