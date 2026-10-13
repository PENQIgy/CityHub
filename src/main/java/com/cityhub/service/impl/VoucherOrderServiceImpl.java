package com.cityhub.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.dto.VoucherOrderVO;
import com.cityhub.entity.VoucherOrder;
import com.cityhub.mapper.VoucherOrderMapper;
import com.cityhub.service.ISeckillVoucherService;
import com.cityhub.service.IVoucherOrderService;
import com.cityhub.utils.RedisWorker;
import com.cityhub.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
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
 * 代金券订单服务实现（秒杀 + 交易闭环）
 * </p>
 *
 * @author CityHub
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    /** 订单状态 */
    private static final int STATUS_UNPAID = 1;
    private static final int STATUS_PAID = 2;
    private static final int STATUS_USED = 3;
    private static final int STATUS_CANCELED = 4;

    @Resource
    private RedisWorker redisWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
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

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /* ==================================================================
     *  秒杀下单
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
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());
        if (result == null) {
            return Result.fail("下单失败，请稍后重试");
        }
        int r = result.intValue();
        if (r != 0) {
            switch (r) {
                case 1:
                    return Result.fail("库存不足");
                case 2:
                    return Result.fail("每人限购一张，请勿重复下单");
                case 3:
                    return Result.fail("该优惠券暂未开抢");
                default:
                    return Result.fail("下单失败");
            }
        }
        // 预校验通过：生成订单号，写入阻塞队列异步落库
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setPayType(1);
        voucherOrder.setStatus(STATUS_UNPAID);
        if (!orderTasks.offer(voucherOrder)) {
            return Result.fail("当前下单人数过多，请稍后重试");
        }
        return Result.ok(orderId);
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            // 修复原实现：原来只 take 一次，处理完一笔订单线程就退出了
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("订单处理线程被中断");
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

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.error("用户已经购买过一次！userId=" + userId + ", voucherId=" + voucherId);
            return;
        }
        // 修复原实现：原来把整个实体当成 voucherId 条件传进去了
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("优惠券库存不足, voucherId=" + voucherId);
            return;
        }
        save(voucherOrder);
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
        // 回补库存（DB + Redis），并释放「一人一单」标记
        seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
        stringRedisTemplate.opsForValue()
                .increment(SECKILL_STOCK_KEY + order.getVoucherId());
        stringRedisTemplate.opsForSet()
                .remove(SECKILL_ORDER_KEY + order.getVoucherId(), userId.toString());
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
