package com.cityhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cityhub.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 查询商户在售代金券。
     *
     * @param shopId 商户id
     * @param userId 当前登录用户id，可为 null（未登录时不统计已购数量）
     */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId, @Param("userId") Long userId);

    /**
     * 扣减普通券库存，库存不足或不在使用时间窗内时返回 0 行（下单失败）。
     *
     * @return 受影响行数，1 表示扣减成功
     */
    int deductStock(@Param("voucherId") Long voucherId);

    /**
     * 回补库存（取消订单时使用），库存不会超过总库存。
     */
    int restoreStock(@Param("voucherId") Long voucherId);

    /**
     * 设置券的时间窗。秒杀券传真实起止时间；普通券（不限时）传 endTime = null。
     * <p>
     * 必须用显式 SQL：MyBatis-Plus 默认会忽略 null 字段，会让表里的
     * {@code DEFAULT CURRENT_TIMESTAMP} 落到 end_time 上，形成「零宽时间窗」，
     * 结果普通券一下单就被判成「已结束」。
     */
    int updateTimeWindow(@Param("voucherId") Long voucherId,
                         @Param("beginTime") LocalDateTime beginTime,
                         @Param("endTime") LocalDateTime endTime);
}
