package com.cityhub.controller;

import com.cityhub.dto.Result;
import com.cityhub.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 代金券订单前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /** 秒杀下单 */
    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /** 普通券下单（同步落库，一人一单） */
    @PostMapping("/buy/{id}")
    public Result buyVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.buyVoucher(voucherId);
    }

    /** 我的订单列表 */
    @GetMapping("/list")
    public Result queryMyOrders(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                @RequestParam(value = "status", required = false) Integer status) {
        return voucherOrderService.queryMyOrders(current, status);
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long id) {
        return voucherOrderService.queryOrderById(id);
    }

    /** 模拟支付 */
    @PutMapping("/pay/{id}")
    public Result payOrder(@PathVariable("id") Long id,
                           @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return voucherOrderService.payOrder(id, payType);
    }

    /** 取消未支付订单 */
    @PutMapping("/cancel/{id}")
    public Result cancelOrder(@PathVariable("id") Long id) {
        return voucherOrderService.cancelOrder(id);
    }

    /** 核销已支付订单 */
    @PutMapping("/use/{id}")
    public Result useOrder(@PathVariable("id") Long id) {
        return voucherOrderService.useOrder(id);
    }
}
