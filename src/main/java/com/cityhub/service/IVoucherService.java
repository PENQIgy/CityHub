package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /** 商户在售优惠券列表（带「当前用户是否已购买」标记） */
    Result queryVoucherOfShop(Long shopId);

    /** 新增普通代金券（不限量，随时可买） */
    Result addVoucher(Voucher voucher);

    /** 新增秒杀代金券（带库存与抢购时间窗，并把库存预热到 Redis） */
    Result addSeckillVoucher(Voucher voucher);
}
