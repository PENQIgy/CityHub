package com.cityhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.entity.SeckillVoucher;
import com.cityhub.entity.Voucher;
import com.cityhub.mapper.VoucherMapper;
import com.cityhub.service.ISeckillVoucherService;
import com.cityhub.service.IVoucherService;
import com.cityhub.utils.SystemConstants;
import com.cityhub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.cityhub.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    /** 优惠券类型：0 普通券，1 秒杀券 */
    private static final int TYPE_NORMAL = 0;
    private static final int TYPE_SECKILL = 1;
    /** 状态：1 上架 */
    private static final int STATUS_ON = 1;
    /** 秒杀库存缓存时长（天） */
    private static final long STOCK_TTL_DAYS = 7L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        if (shopId == null) {
            return Result.fail("商户不存在");
        }
        // 未登录时 userId 传 null，已购标记自然为 0
        Long userId = UserHolder.getUserId();
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId, userId);
        // 普通券没有秒杀记录，库存按「不限量」处理（-1），前端据此显示为可购买
        for (Voucher voucher : vouchers) {
            if (voucher.getStock() == null) {
                voucher.setStock(-1);
            }
            if (voucher.getBought() == null) {
                voucher.setBought(0);
            }
        }
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public Result addVoucher(Voucher voucher) {
        String error = validate(voucher, false);
        if (error != null) {
            return Result.fail(error);
        }
        voucher.setType(TYPE_NORMAL);
        voucher.setStatus(STATUS_ON);
        // 普通券不限量：写一条库存记录（也便于后续扩展限量普通券）
        if (voucher.getStock() == null || voucher.getStock() <= 0) {
            voucher.setStock(SystemConstants.VOUCHER_UNLIMITED_STOCK);
        }
        save(voucher);
        SeckillVoucher stockRow = new SeckillVoucher();
        stockRow.setVoucherId(voucher.getId());
        stockRow.setStock(voucher.getStock());
        seckillVoucherService.save(stockRow);
        // 普通券不限时：end_time 显式写成 NULL。表里两列的默认值是 CURRENT_TIMESTAMP，
        // 不显式清掉就会落成 begin_time == end_time 的「零宽时间窗」，下单直接被判「已结束」。
        getBaseMapper().updateTimeWindow(voucher.getId(), LocalDateTime.now(), null);
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional
    public Result addSeckillVoucher(Voucher voucher) {
        String error = validate(voucher, true);
        if (error != null) {
            return Result.fail(error);
        }
        voucher.setType(TYPE_SECKILL);
        voucher.setStatus(STATUS_ON);
        save(voucher);

        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 库存预热到 Redis：秒杀下单走 Lua 脚本，依赖这个 key
        stringRedisTemplate.opsForValue().set(
                SECKILL_STOCK_KEY + voucher.getId(),
                String.valueOf(voucher.getStock()),
                STOCK_TTL_DAYS, TimeUnit.DAYS);
        return Result.ok(voucher.getId());
    }

    /* ==================================================================
     *  创建优惠券的参数校验
     * ================================================================== */

    /**
     * @param seckill true 表示秒杀券（必须校验库存与使用时间）
     * @return null 表示校验通过，否则返回中文错误提示
     */
    private String validate(Voucher voucher, boolean seckill) {
        if (voucher == null) {
            return "优惠券信息不能为空";
        }
        if (voucher.getShopId() == null) {
            return "请选择所属商户";
        }
        if (isBlank(voucher.getTitle())) {
            return "请填写优惠券名称";
        }
        if (voucher.getTitle().length() > 100) {
            return "优惠券名称最长 100 个字";
        }
        if (voucher.getPayValue() == null || voucher.getPayValue() <= 0) {
            return "请填写正确的支付金额";
        }
        if (voucher.getActualValue() == null || voucher.getActualValue() <= 0) {
            return "请填写正确的抵扣金额";
        }
        if (voucher.getPayValue() > voucher.getActualValue()) {
            return "支付金额不能大于抵扣金额";
        }
        if (voucher.getPayValue() > 100_000_00L || voucher.getActualValue() > 100_000_00L) {
            return "金额超出合理范围";
        }
        if (voucher.getStock() != null && voucher.getStock() < 0) {
            return "库存不能为负数";
        }
        if (seckill) {
            if (voucher.getStock() == null || voucher.getStock() <= 0) {
                return "秒杀券库存必须大于 0";
            }
            if (voucher.getBeginTime() == null || voucher.getEndTime() == null) {
                return "请选择使用时间（开始 / 结束时间）";
            }
            if (!voucher.getEndTime().isAfter(voucher.getBeginTime())) {
                return "结束时间必须晚于开始时间";
            }
            if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
                return "结束时间不能早于当前时间";
            }
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
