package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 代金券订单服务类
 * </p>
 *
 * @author CityHub
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /** 秒杀下单（Redis + Lua 预校验，异步落库） */
    Result seckillVoucher(Long voucherId);

    /** 普通券下单（同步落库，一人一单） */
    Result buyVoucher(Long voucherId);

    /** 异步落库，由下单线程调用 */
    void createVoucherOrder(VoucherOrder voucherOrder);

    /** 我的订单列表 */
    Result queryMyOrders(Integer current, Integer status);

    /** 订单详情 */
    Result queryOrderById(Long id);

    /** 模拟支付 */
    Result payOrder(Long id, Integer payType);

    /** 取消未支付订单（并回补库存） */
    Result cancelOrder(Long id);

    /** 核销已支付订单 */
    Result useOrder(Long id);
}
