package com.cityhub.controller;


import com.cityhub.dto.Result;
import com.cityhub.entity.Voucher;
import com.cityhub.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券（不限量，随时可买）。
     *
     * @param voucher 优惠券信息：shopId 所属商户、title 名称、payValue 支付金额（分）、
     *                actualValue 抵扣金额（分）、rules 使用规则
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        return voucherService.addVoucher(voucher);
    }

    /**
     * 新增秒杀券。
     *
     * @param voucher 优惠券信息，额外包含 stock 库存、beginTime / endTime 抢购与使用时间
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        return voucherService.addSeckillVoucher(voucher);
    }

    /**
     * 查询店铺的优惠券列表（带「当前用户是否已购买」标记）。
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}
