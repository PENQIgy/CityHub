package com.cityhub.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.dto.VoucherOrderVO;
import com.cityhub.entity.SeckillVoucher;
import com.cityhub.entity.Voucher;
import com.cityhub.entity.VoucherOrder;
import com.cityhub.mapper.VoucherMapper;
import com.cityhub.mapper.VoucherOrderMapper;
import com.cityhub.service.ISeckillVoucherService;
import com.cityhub.service.IVoucherOrderService;
import com.cityhub.service.IVoucherService;
import com.cityhub.utils.RedisWorker;
import com.cityhub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cityhub.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.cityhub.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 代金券订单服务实现（秒杀 + 普通券下单 + 交易闭环）
 * </p>
 * <p>
 * 「一人一单」的三层保障：
 * <ol>
 *   <li>Redis：下单前先跑 seckill.lua，在 Redis 内原子完成「时间窗 + 库存 + 一人一单 + 扣减」；</li>
 *   <li>应用层：落库前再查一次有效订单（未支付 / 已支付 / 已核销），已取消的不占用名额；</li>
 *   <li>数据库：tb_voucher_order 上的唯一索引 uk_order_user_voucher(user_id, voucher_id) 兜底，
 *       即使前两层被并发绕过，同一个用户对同一张券也只会留下一条订单。</li>
 * </ol>
 *
 * @author CityHub
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    /** 订单状态 */
    private static final int STATUS_UNPAID = 1;
    private static final int STATUS_PAID = 2;
    private static final int STATUS_USED = 3;
    private static final int STATUS_CANCELED = 4;

    /** 优惠券类型：1 秒杀券 */
    private static final int TYPE_SECKILL = 1;
    /** 优惠券状态：1 上架 */
    private static final int STATUS_ON = 1;

    /* --- seckill.lua 返回码 --- */
    private static final int CODE_OK = 0;
    private static final int CODE_BOUGHT = 1;
    private static final int CODE_NO_STOCK = 2;
    private static final int CODE_NOT_WARMED = 3;
    private static final int CODE_OUT_OF_TIME = 4;

    /** 秒杀下单后，等待订单异步落库的最长时间（毫秒）与轮询间隔 */
    private static final long ORDER_PERSIST_TIMEOUT_MS = 3000L;
    private static final long ORDER_PERSIST_POLL_MS = 40L;

    @Resource
    private RedisWorker redisWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    /**
     * 事务自引用：不能在 @PostConstruct 里直接取，否则会触发自身 Bean 的循环依赖，
     * 这里用 ObjectProvider 延迟到订单真正落库时再解析（拿到的是 Spring 的事务代理）。
     */
    @Resource
    private ObjectProvider<IVoucherOrderService> selfProvider;

    /** 阻塞队列：Redis 预校验通过后写入，由单线程异步落库 */
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cityhub-order-handler");
        t.setDaemon(true);
        return t;
    });

    /** 事务代理，保证 createVoucherOrder 的 @Transactional 生效 */
    private IVoucherOrderService self() {
        return selfProvider.getObject();
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_ORDER_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        RELEASE_ORDER_SCRIPT = new DefaultRedisScript<>();
        RELEASE_ORDER_SCRIPT.setLocation(new ClassPathResource("release-order.lua"));
        RELEASE_ORDER_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /* ==================================================================
     *  下单入口：/voucher-order/seckill/{id}（秒杀券与普通券共用）
     * ================================================================== */

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (voucherId == null) {
            return Result.fail("优惠券不存在");
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || !Objects.equals(voucher.getStatus(), STATUS_ON)) {
            return Result.fail("优惠券不存在或已下架");
        }
        if (Objects.equals(voucher.getType(), TYPE_SECKILL)) {
            return seckillVoucher(userId, voucher);
        }
        // 非秒杀券落到普通下单通道
        return buyVoucher(userId, voucher);
    }

    /* ==================================================================
     *  下单：秒杀券（Redis 预校验 + 异步落库）
     * ================================================================== */

    private Result seckillVoucher(Long userId, Voucher voucher) {
        Long voucherId = voucher.getId();
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        String beginMillis = toMillis(seckillVoucher == null ? null : seckillVoucher.getBeginTime());
        String endMillis = toMillis(seckillVoucher == null ? null : seckillVoucher.getEndTime());

        Long result;
        try {
            result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                    voucherId.toString(), userId.toString(), beginMillis, endMillis, String.valueOf(nowMillis()));
        } catch (Exception e) {
            log.error("秒杀脚本执行失败, voucherId={}, userId={}", voucherId, userId, e);
            return Result.fail("下单失败，请稍后重试");
        }
        if (result == null) {
            return Result.fail("下单失败，请稍后重试");
        }
        int r = result.intValue();
        if (r != CODE_OK) {
            switch (r) {
                case CODE_BOUGHT:
                    return Result.fail("每人限购一张，请勿重复下单");
                case CODE_NO_STOCK:
                    return Result.fail("库存不足");
                case CODE_OUT_OF_TIME:
                    return Result.fail("该优惠券不在抢购时间内");
                case CODE_NOT_WARMED:
                default:
                    return Result.fail("该优惠券暂未开抢");
            }
        }
        // 预校验通过：生成订单号，写入阻塞队列异步落库
        long orderId = redisWorker.nextId("order");

        // ⚠️ 「取消后重新下单」必须在这里（主线程）就定好最终订单号。
        // 如果交给异步线程去复用已取消的订单行，主线程会拿着新生成的 id 去等落库，
        // 而真正落库的是被复用的旧 id，结果必然等到超时（前端表现为下单失败）。
        VoucherOrder canceled = getBaseMapper().selectCanceledOrder(userId, voucherId);
        if (canceled != null) {
            if (getBaseMapper().reactivateOrder(canceled.getId()) == 1) {
                return Result.ok(canceled.getId());
            }
            // 极端并发下被别人抢先改掉了，落回下面的正常下单流程
            log.warn("复用已取消订单失败，改为新建订单, userId={}, voucherId={}", userId, voucherId);
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1);
        voucherOrder.setStatus(STATUS_UNPAID);
        voucherOrder.setFromSeckill(true);
        if (!orderTasks.offer(voucherOrder)) {
            // 队列已满：把 Lua 里预扣的库存与「一人一单」标记还回去，否则用户白丢一次机会
            releasePreCheck(voucherId, userId, true);
            return Result.fail("当前下单人数过多，请稍后重试");
        }
        // 队列只是「异步落库」的缓冲，但接口不能拿着一个还没写进数据库的订单号就返回，
        // 否则前端立刻去支付/取消会拿到「订单不存在」。单线程消费，通常几十毫秒。
        if (!waitOrderPersisted(orderId)) {
            log.error("订单落库超时, orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
            return Result.fail("订单提交中，请稍后到「我的订单」查看");
        }
        return Result.ok(orderId);
    }

    /** 轮询等待异步落库完成，最多等 {@link #ORDER_PERSIST_TIMEOUT_MS} 毫秒 */
    private boolean waitOrderPersisted(long orderId) {
        long deadline = System.currentTimeMillis() + ORDER_PERSIST_TIMEOUT_MS;
        do {
            if (getById(orderId) != null) {
                return true;
            }
            try {
                Thread.sleep(ORDER_PERSIST_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return getById(orderId) != null;
    }

    /* ==================================================================
     *  下单：普通券（同步落库，立即返回订单号）
     * ================================================================== */

    @Override
    public Result buyVoucher(Long voucherId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (voucherId == null) {
            return Result.fail("优惠券不存在");
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在或已下架");
        }
        return buyVoucher(userId, voucher);
    }

    private Result buyVoucher(Long userId, Voucher voucher) {
        Long voucherId = voucher.getId();
        Result checked = checkBuyable(voucher);
        if (checked != null) {
            return checked;
        }
        // Redis 预校验：同一个用户买同一张券只能成功一次
        if (!preCheckNormalVoucher(voucher, userId)) {
            return Result.fail("每人限购一张，请勿重复下单");
        }
        // 该用户在这张券上已经取消了订单：直接复用原来那一行（唯一索引要求一人一券只有一行）。
        // 注意这里**不能**再走 createVoucherOrder，因为它会再扣一次库存——库存已经在 Lua 预校验里扣过了。
        VoucherOrder canceled = getBaseMapper().selectCanceledOrder(userId, voucherId);
        if (canceled != null && getBaseMapper().reactivateOrder(canceled.getId()) == 1) {
            return Result.ok(canceled.getId());
        }

        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1);
        voucherOrder.setStatus(STATUS_UNPAID);
        try {
            self().createVoucherOrder(voucherOrder);
        } catch (DuplicateKeyException e) {
            // 数据库唯一索引兜底：并发下只会留下一条订单
            log.warn("并发重复下单已被唯一索引拦截, userId={}, voucherId={}", userId, voucherId);
            return Result.fail("每人限购一张，请勿重复下单");
        } catch (Exception e) {
            log.error("普通券下单失败, userId={}, voucherId={}", userId, voucherId, e);
            releasePreCheck(voucherId, userId, false);
            return Result.fail("下单失败，请稍后重试");
        }
        return Result.ok(orderId);
    }

    /**
     * 券下单前的基础校验（上下架 + 秒杀券的抢购时间窗）。
     * <p>
     * 注意：普通券（type = 0）不限时，随时可买，**不能**用 tb_seckill_voucher 里的时间判定，
     * 那张表对普通券只是用来存 stock = -1（不限量）的占位记录，其时间列没有业务含义。
     *
     * @return null 表示校验通过
     */
    private Result checkBuyable(Voucher voucher) {
        if (!Objects.equals(voucher.getStatus(), STATUS_ON)) {
            return Result.fail("优惠券不存在或已下架");
        }
        // 只有秒杀券才有「抢购时间窗」
        if (!Objects.equals(voucher.getType(), TYPE_SECKILL)) {
            return null;
        }
        SeckillVoucher window = seckillVoucherService.getById(voucher.getId());
        if (window == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (window.getBeginTime() != null && now.isBefore(window.getBeginTime())) {
            return Result.fail("该优惠券暂未开抢");
        }
        if (window.getEndTime() != null && now.isAfter(window.getEndTime())) {
            return Result.fail("该优惠券已结束");
        }
        return null;
    }

    /** 普通券的 Redis 预校验（库存 + 一人一单），返回 true 表示可以继续下单 */
    private boolean preCheckNormalVoucher(Voucher voucher, Long userId) {
        Long voucherId = voucher.getId();
        String orderKey = SECKILL_ORDER_KEY + voucherId;
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        // 已经买过（含已支付 / 已核销）直接拒绝，不用再动 Redis
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(orderKey, userId.toString()))) {
            return false;
        }
        // 库存 key 缺失时补一个（普通券不限量，用 -1 表示不限量，Lua 会继续往下减）
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().set(stockKey, "-1");
        }
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), "", "", String.valueOf(nowMillis()));
        int code = result == null ? CODE_NOT_WARMED : result.intValue();
        if (code == CODE_OK) {
            return true;
        }
        // 不限量券的库存是负数占位，取消订单时会被加回 0；这里自愈一次再重试
        if (code == CODE_NO_STOCK && !isLimitedStock(voucherId)) {
            stringRedisTemplate.opsForValue().set(stockKey, "-1");
            result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                    voucherId.toString(), userId.toString(), "", "", String.valueOf(nowMillis()));
            return result != null && result.intValue() == CODE_OK;
        }
        return false;
    }

    /* ==================================================================
     *  订单落库（唯一入口，事务）
     * ================================================================== */

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            // 修复原实现：原来只 take 一次，处理完一笔订单线程就退出了
            while (!Thread.currentThread().isInterrupted()) {
                VoucherOrder voucherOrder = null;
                try {
                    voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("订单处理线程被中断");
                } catch (DuplicateKeyException e) {
                    // 唯一索引兜底命中：把预扣的库存与「一人一单」标记还给用户
                    if (voucherOrder != null) {
                        log.warn("重复订单被唯一索引拦截, userId={}, voucherId={}",
                                voucherOrder.getUserId(), voucherOrder.getVoucherId());
                        releasePreCheck(voucherOrder.getVoucherId(), voucherOrder.getUserId(), true);
                    }
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // Redisson 的无参 tryLock() 内部已经吞掉了 InterruptedException，不需要再捕获
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单, userId=" + userId);
            return;
        }
        try {
            self().createVoucherOrder(voucherOrder);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 写入订单：一人一单校验 + 库存扣减（秒杀已在 Lua 中扣过，不再重复扣）。
     * <p>
     * 通过 {@code self().createVoucherOrder(...)} 调用，保证 @Transactional 生效；
     * 唯一索引冲突会抛出 DuplicateKeyException，由调用方兜底处理。
     * <p>
     * 注意：这里**不负责**「取消后复用原订单行」。复用必须在调用方（web 线程）就定好订单号，
     * 否则会出现「主线程拿新 id 等落库、异步线程把 id 换成旧订单号」的错位，表现为下单超时。
     */
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 一人一单：已取消的订单不占用名额，可以重新下单
        VoucherOrder existing = getBaseMapper().findActiveOrder(userId, voucherId);
        if (existing != null) {
            log.warn("用户已经购买过一次, userId={}, voucherId={}, orderId={}",
                    userId, voucherId, existing.getId());
            return;
        }

        // 扣减库存：秒杀券的库存在 Lua 脚本里已经扣过；普通券支持限量（负数表示不限量）
        if (!Boolean.TRUE.equals(voucherOrder.getFromSeckill()) && isLimitedStock(voucherId)) {
            if (voucherMapper.deductStock(voucherId) == 0) {
                log.error("优惠券库存不足, voucherId={}", voucherId);
                throw new IllegalStateException("库存不足");
            }
        }

        save(voucherOrder);
    }

    /**
     * 判断该券是否有真实库存概念。
     * <p>
     * 只有 {@code stock > 0} 才算限量：{@code stock <= 0}（含普通券落库的 0、缺失记录、负数占位）
     * 全部按「不限量」处理。这里**不能**用 {@code >= 0}——普通券的不限量标记是 0，
     * 会被误判成「限量且已售罄」，取消后重新下单就会报「库存不足」。
     */
    private boolean isLimitedStock(Long voucherId) {
        SeckillVoucher stock = seckillVoucherService.getById(voucherId);
        return stock != null && stock.getStock() != null && stock.getStock() > 0;
    }

    /** 释放 Redis 预校验占用的资源（库存 + 一人一单标记） */
    private void releasePreCheck(Long voucherId, Long userId, boolean seckill) {
        try {
            if (seckill) {
                stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            }
            stringRedisTemplate.execute(RELEASE_ORDER_SCRIPT, Collections.emptyList(),
                    String.valueOf(voucherId), String.valueOf(userId));
        } catch (Exception e) {
            log.error("释放预校验标记失败, voucherId={}, userId={}", voucherId, userId, e);
        }
    }

    private String toMillis(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return String.valueOf(time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /** 当前时间戳（毫秒），传给 Lua 做时间窗判断 */
    private long nowMillis() {
        return System.currentTimeMillis();
    }

    /* ==================================================================
     *  我的订单 / 支付 / 取消 / 核销
     * ================================================================== */

    @Override
    public Result queryMyOrders(Integer current, Integer status) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        int pageNo = current == null || current < 1 ? 1 : current;
        IPage<VoucherOrderVO> page = getBaseMapper().queryOrderPage(
                new Page<>(pageNo, 10, false), userId, status);
        return Result.ok(page.getRecords());
    }

    @Override
    public Result queryOrderById(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        VoucherOrderVO vo = getBaseMapper().queryOrderDetail(id);
        if (vo == null) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(vo.getUserId(), userId)) {
            return Result.fail("无权查看他人订单");
        }
        return Result.ok(vo);
    }

    @Override
    public Result payOrder(Long id, Integer payType) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(id);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getStatus(), STATUS_UNPAID)) {
            return Result.fail("当前订单状态不支持支付");
        }
        update().set("status", STATUS_PAID)
                .set("pay_type", payType == null ? 1 : payType)
                .set("pay_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", STATUS_UNPAID)
                .update();
        return Result.ok();
    }

    @Transactional
    @Override
    public Result cancelOrder(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(id);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getStatus(), STATUS_UNPAID)) {
            return Result.fail("只有未支付的订单才能取消");
        }
        boolean updated = update().set("status", STATUS_CANCELED)
                .eq("id", id)
                .eq("status", STATUS_UNPAID)
                .update();
        if (!updated) {
            return Result.fail("订单状态已变更，请刷新后重试");
        }
        // 回补库存（DB），并释放「一人一单」标记，让用户可以重新下单
        Long voucherId = order.getVoucherId();
        voucherMapper.restoreStock(voucherId);
        // 不限量券（普通券）的库存是负数占位，回补成 0 会让下次下单被判定为「库存不足」，
        // 这里统一重置为 -1；限量券才做真正的 +1 回补。
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(SECKILL_STOCK_KEY + voucherId))) {
            if (isLimitedStock(voucherId)) {
                stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            } else {
                stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucherId, "-1");
            }
        }
        stringRedisTemplate.opsForSet()
                .remove(SECKILL_ORDER_KEY + voucherId, userId.toString());
        return Result.ok();
    }

    @Override
    public Result useOrder(Long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(id);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            return Result.fail("订单不存在");
        }
        if (!Objects.equals(order.getStatus(), STATUS_PAID)) {
            return Result.fail("只有已支付的订单才能核销");
        }
        update().set("status", STATUS_USED)
                .set("use_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", STATUS_PAID)
                .update();
        return Result.ok();
    }
}
