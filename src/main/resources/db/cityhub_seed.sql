-- =====================================================================
--  CityHub 演示数据（可选）
--  为评论 / 收藏 / 交易 等新模块铺一些真实可看的数据，方便联调与演示
--  可重复执行（幂等）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 评论数据：一级评论 + 二级回复
-- ---------------------------------------------------------------------
INSERT INTO `tb_blog_comments` (`user_id`, `blog_id`, `parent_id`, `answer_id`, `content`, `images`, `liked`, `status`)
SELECT * FROM (
    SELECT 1011 AS a, 4 AS b, 0 AS c, 0 AS d, '这家我也去过！环境很安静，适合坐着发呆一下午。' AS e, NULL AS f, 6 AS g, 0 AS h
    UNION ALL SELECT 1012, 4, 0, 0, '请问周末去要排队吗？', NULL, 3, 0
    UNION ALL SELECT 1013, 4, 0, 0, '拍照超出片，随手一拍都是壁纸。', '/imgs/types/jd.png', 9, 0
    UNION ALL SELECT 1,    4, 2, 2, '要的，建议下午三点前去，人会少一些。', NULL, 2, 0
    UNION ALL SELECT 1011, 5, 0, 0, '价格很实在，分量也足，回购第三次了。', NULL, 5, 0
    UNION ALL SELECT 1012, 5, 0, 0, '老板人很nice，还送了小菜。', NULL, 4, 0
    UNION ALL SELECT 1013, 6, 0, 0, '位置有点隐蔽，跟着导航走还是很好找的。', NULL, 1, 0
    UNION ALL SELECT 1011, 23, 0, 0, '写得太好了，周末就去打卡！', NULL, 12, 0
    UNION ALL SELECT 1012, 23, 0, 0, '这个角度拍得真好看，求机位。', NULL, 7, 0
    UNION ALL SELECT 1013, 23, 9, 9, '就在门口左手边，逆光拍最好看。', NULL, 3, 0
    UNION ALL SELECT 1,    23, 0, 0, '感谢分享，已经收藏了。', NULL, 5, 0
    UNION ALL SELECT 1012, 24, 0, 0, '性价比之王，学生党友好。', NULL, 8, 0
) t
WHERE NOT EXISTS (SELECT 1 FROM `tb_blog_comments` LIMIT 1);

-- ---------------------------------------------------------------------
-- 2. 收藏数据
-- ---------------------------------------------------------------------
INSERT INTO `tb_blog_collect` (`user_id`, `blog_id`)
SELECT * FROM (
    SELECT 1011 AS a, 4 AS b
    UNION ALL SELECT 1011, 23
    UNION ALL SELECT 1012, 4
    UNION ALL SELECT 1012, 23
    UNION ALL SELECT 1012, 24
    UNION ALL SELECT 1013, 5
    UNION ALL SELECT 1,    23
    UNION ALL SELECT 2,    24
) t
WHERE NOT EXISTS (SELECT 1 FROM `tb_blog_collect` LIMIT 1);

-- ---------------------------------------------------------------------
-- 3. 交易数据：代金券订单（已支付 / 待支付 / 已核销）
-- ---------------------------------------------------------------------
INSERT INTO `tb_voucher_order` (`id`, `user_id`, `voucher_id`, `pay_type`, `status`, `create_time`, `pay_time`)
SELECT * FROM (
    SELECT 1000000000000000001 AS a, 1011 AS b, 1 AS c, 1 AS d, 2 AS e, NOW() - INTERVAL 3 DAY AS f, NOW() - INTERVAL 3 DAY AS g
    UNION ALL SELECT 1000000000000000002, 1011, 2, 1, 3, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY
    UNION ALL SELECT 1000000000000000003, 1012, 3, 2, 1, NOW() - INTERVAL 2 HOUR, NULL
    UNION ALL SELECT 1000000000000000004, 1013, 4, 3, 2, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY
) t
WHERE NOT EXISTS (SELECT 1 FROM `tb_voucher_order` LIMIT 1);

-- ---------------------------------------------------------------------
-- 3.5 原始演示数据润色
--     老库里 tb_blog 的标题/正文是「111」「222」「奥奥」这类占位内容，
--     作为内容平台的演示数据太难看了，这里替换成真实的探店文案，
--     并把话题、定位与所属商户对齐（可重复执行）
-- ---------------------------------------------------------------------

-- 4 / 5 / 6 / 7 是原始种子笔记，正文与话题重新对齐
UPDATE `tb_blog` SET
    `topic_id` = 1,
    `location` = '拱宸桥/上塘',
    `content`  = '提前一周才订到的位置，餐厅在乐堤港的临河一侧，落地窗正对着运河。\n战斧牛排是招牌，三分熟切面很漂亮，配的红酒汁偏酸甜口。\n晚上八点以后灯光会调暗，氛围一下子起来了，适合纪念日。\n人均 300 左右，服务费另算，建议提前订位。'
WHERE `id` = 4;

UPDATE `tb_blog` SET
    `topic_id` = 5,
    `location` = '大关',
    `content`  = '大关这家港式茶餐厅开了十几年，晚上十点还在翻台。\n必点冻柠茶和菠萝油，菠萝油要趁热吃，黄油化开那一下最香。\n干炒牛河锅气很足，牛肉给得也大方。\n人均 30 出头，夜宵档的性价比之王。'
WHERE `id` = 5;

UPDATE `tb_blog` SET
    `shop_id`  = NULL,
    `topic_id` = 6,
    `location` = '余杭',
    `content`  = '马场在余杭，市区开车过去四十分钟。\n50 块钱可以骑三圈，教练全程牵着，五岁的小朋友也能上。\n除了骑马还有喂小羊、挖沙池，娃能玩一整个下午。\n建议穿长裤和运动鞋，草地里蚊子有点多。'
WHERE `id` = 6;

UPDATE `tb_blog` SET
    `shop_id`  = NULL,
    `topic_id` = 3,
    `location` = '拱墅区',
    `title`    = '杭州周末好去处｜沿运河骑行看日落，人少又出片',
    `content`  = '沿着运河绿道从大关骑到拱宸桥，全程大概四十分钟。\n傍晚六点半出发，正好赶上日落，桥上的角度最出片。\n沿途有补给点，骑车累了可以坐下来喝杯东西。\n共享单车就能走，人比西湖边少太多了。'
WHERE `id` = 7;

UPDATE `tb_blog` SET
    `title`    = '大关这家茶餐厅，凌晨还在排队｜港式夜宵天花板',
    `topic_id` = 5,
    `location` = '大关',
    `content`  = '在 CityHub 刷到这家，当晚就冲了。\n店面不大，晚上十一点门口还坐着等位的人。\n冻柠茶茶味很浓，不齁甜；菠萝油现烤的，酥皮一碰就掉。\n干炒牛河是那种带着锅气的香，牛肉不柴。\n两个人吃了 68，直接封神。'
WHERE `id` = 23;

UPDATE `tb_blog` SET
    `title`    = '拱宸桥铜锅涮肉｜人均80吃到扶墙出',
    `topic_id` = 1,
    `location` = '拱宸桥/上塘',
    `content`  = '老北京铜锅涮羊肉，锅底是清汤配葱姜，最考验肉的品质。\n手切鲜羊肉纹理很清楚，涮八秒就能吃，一点都不膻。\n麻酱是店里自己调的，加一勺韭菜花更香。\n两个人点了三盘肉加一堆配菜，结账 160，性价比很高。'
WHERE `id` = 24;

UPDATE `tb_blog` SET
    `title`    = '水晶城海底捞｜被服务狠狠拿捏了',
    `topic_id` = 1,
    `location` = '大关',
    `content`  = '朋友生日来的，进门就被送了长寿面和果盘。\n锅底选了番茄加麻辣，番茄锅煮到最后下面条很绝。\n虾滑现打的很弹，毛肚七上八下刚刚好。\n等位的时候做了美甲，小朋友还有游乐区，确实省心。'
WHERE `id` = 25;

UPDATE `tb_blog` SET
    `title`    = '运河上街新白鹿｜人均50的杭帮菜，闭眼冲',
    `topic_id` = 2,
    `location` = '运河上街',
    `content`  = '工作日下午五点半到店，不用等位。\n蛋黄鸡翅是招牌，外脆里嫩，两个人分刚好。\n西湖牛肉羹和糖醋里脊都在线，量比想象中大。\n三个人点了五个菜加主食，一共 156，太适合聚餐了。'
WHERE `id` = 26;

UPDATE `tb_blog` SET
    `title`    = '乐堤港 Mamala｜适合坐一下午的临河位置',
    `topic_id` = 4,
    `location` = '拱宸桥/上塘',
    `content`  = '下午两点过来的，人不算多，窗边位置直接坐下。\n手冲是浅烘的果酸调，配一块芝士蛋糕刚好。\n店里很安静，带电脑来办公的人不少，插座也够用。\n坐到五点看运河的夕阳，一整天都很松弛。'
WHERE `id` = 27;

-- ---------------------------------------------------------------------
-- 4. 计数一致性校正（每次都跑，保证冗余计数与明细一致）
-- ---------------------------------------------------------------------
UPDATE `tb_blog` b
SET b.`comments` = (SELECT COUNT(*) FROM `tb_blog_comments` c WHERE c.`blog_id` = b.`id` AND c.`status` = 0),
    b.`collected` = (SELECT COUNT(*) FROM `tb_blog_collect` k WHERE k.`blog_id` = b.`id`);

UPDATE `tb_topic` t
SET t.`blog_count` = (SELECT COUNT(*) FROM `tb_blog` b WHERE b.`topic_id` = t.`id` AND b.`status` = 0);

UPDATE `tb_user_info` i
SET i.`followee`   = (SELECT COUNT(*) FROM `tb_follow` f WHERE f.`user_id` = i.`user_id`),
    i.`fans`       = (SELECT COUNT(*) FROM `tb_follow` f WHERE f.`follow_user_id` = i.`user_id`),
    i.`blog_count` = (SELECT COUNT(*) FROM `tb_blog` b WHERE b.`user_id` = i.`user_id` AND b.`status` = 0);

UPDATE `tb_user_info` i
SET i.`liked_count` = COALESCE((SELECT SUM(b.`liked`) FROM `tb_blog` b WHERE b.`user_id` = i.`user_id` AND b.`status` = 0), 0);
