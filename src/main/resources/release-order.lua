-- =====================================================================
--  CityHub 释放「一人一单」标记脚本
--  KEYS: 无
--  ARGV: [1] voucherId  [2] userId
--  场景：订单取消 / 下单落库失败时，让该用户可以重新下单
--  返回：1 释放完成
-- =====================================================================

local voucherId = ARGV[1]
local userId = ARGV[2]

local orderKey = 'seckill:order:' .. voucherId
redis.call('srem', orderKey, userId)
return 1
