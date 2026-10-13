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
}
