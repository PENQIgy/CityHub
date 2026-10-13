package com.cityhub.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单列表 / 详情视图：订单 + 代金券 + 商户。
 *
 * @author CityHub
 */
@Data
public class VoucherOrderVO {
    /* ---- tb_voucher_order ---- */
    private Long id;
    private Long userId;
    private Long voucherId;
    /** 支付方式 1：余额支付；2：支付宝；3：微信 */
    private Integer payType;
    /** 1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;

    /* ---- tb_voucher ---- */
    private String title;
    private String subTitle;
    private String rules;
    /** 支付金额（分） */
    private Long payValue;
    /** 抵扣金额（分） */
    private Long actualValue;
    private Integer voucherType;

    /* ---- tb_shop ---- */
    private Long shopId;
    private String shopName;
    private String shopImage;
    private String shopAddress;
}
