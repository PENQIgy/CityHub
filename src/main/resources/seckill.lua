-- =====================================================================
--  CityHub 秒杀下单预校验脚本（库存 + 一人一单 + 抢购时间窗）
--  KEYS: 无
--  ARGV: [1] voucherId  [2] userId
--        [3] beginTimeMillis  [4] endTimeMillis（空串表示不限制）
--        [5] nowMillis（当前时间，由应用层传入，避免在脚本里调用 TIME）
--
--  返回码（与 VoucherOrderServiceImpl 中的分支严格对应）:
--    0 校验通过（已扣减库存并登记下单用户）
--    1 该用户已经购买过（一人一单）
--    2 库存不足
--    3 库存未预热
--    4 不在抢购时间窗内
-- =====================================================================

local voucherId = ARGV[1]
local userId = ARGV[2]
local beginMillis = ARGV[3]
local endMillis = ARGV[4]
local nowMillis = tonumber(ARGV[5])

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 1. 抢购时间窗校验（防止前端的时间判断被绕过）
if (nowMillis and beginMillis and beginMillis ~= '' and nowMillis < tonumber(beginMillis)) then
    return 4
end
if (nowMillis and endMillis and endMillis ~= '' and nowMillis > tonumber(endMillis)) then
    return 4
end

-- 2. 库存校验
local stock = redis.call('get', stockKey)
if (not stock) then
    -- 库存未预热，交由应用层提示「暂未开抢」
    return 3
end
if (tonumber(stock) <= 0 and tonumber(stock) >= 0) then
    return 2
end

-- 3. 一人一单校验（同一个用户对同一张券只允许下一次单）
if (redis.call('sismember', orderKey, userId) == 1) then
    return 1
end

-- 4. 原子扣减库存并登记下单用户
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)

return 0
