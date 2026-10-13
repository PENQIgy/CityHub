package com.cityhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cityhub.dto.VoucherOrderVO;
import com.cityhub.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 代金券订单 Mapper
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /**
     * 分页查询订单，并带出代金券与商户信息。
     *
     * @param page   分页对象
     * @param userId 下单用户
     * @param status 订单状态，null 表示不过滤
     */
    IPage<VoucherOrderVO> queryOrderPage(IPage<VoucherOrderVO> page,
                                         @Param("userId") Long userId,
                                         @Param("status") Integer status);

    /**
     * 查询单条订单详情。
     */
    VoucherOrderVO queryOrderDetail(@Param("id") Long id);

    /**
     * 查询某用户在某张券上「有效」的订单（未支付 / 已支付 / 已核销）。
     * 已取消的订单不算占用名额，用户取消后可以重新下单。
     */
    VoucherOrder findActiveOrder(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    /**
     * 查询某用户在某张券上「已取消」的订单，用于取消后重新下单时复用原订单行。
     */
    VoucherOrder selectCanceledOrder(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    /**
     * 把已取消的订单重新激活成未支付订单（重新下单时复用原订单号）。
     *
     * @return 受影响行数，1 表示重新激活成功
     */
    int reactivateOrder(@Param("id") Long id);
}
