package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 商户服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    /** 关键词搜索商户（名称 / 商圈 / 地址） */
    Result searchShop(String keyword, Integer current);
}
