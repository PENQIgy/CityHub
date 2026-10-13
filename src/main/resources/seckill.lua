-- =====================================================================
--  CityHub 秒杀预校验脚本
--  KEYS: 无
--  ARGV: [1] voucherId  [2] userId
--  返回: 0 通过 / 1 库存不足 / 2 重复下单 / 3 库存未初始化
-- =====================================================================

-- 1. 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 2. 数据 key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.1 判断库存是否充足
local stock = redis.call('get', stockKey)
if (not stock) then
    -- 库存未预热，交由应用层提示「暂未开抢」
    return 3
end
-- 注意：原实现写成了 tonum（Lua 中不存在该函数），会导致脚本直接报错
if (tonumber(stock) <= 0) then
    return 1
end

-- 3.2 判断用户是否已经下过单（集合 sismember）
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3.3 扣减库存
redis.call('incrby', stockKey, -1)
-- 3.4 记录下单用户，标记该用户已经买过
redis.call('sadd', orderKey, userId)

-- 3.5 一切正常
return 0
