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

    /** 更新商户（同时刷新缓存与 GEO 索引） */
    Result update(Shop shop);

    /** 新增商户（校验 + 落库 + 写入 Redis GEO 索引） */
    Result createShop(Shop shop);

    /**
     * 按类型分页查询商户。
     *
     * @param current  页码
     * @param x        用户经度，可为空
     * @param y        用户纬度，可为空
     * @param radiusKm 只看该半径内的商户，为空表示不限半径（全部商户按距离升序）
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y, Double radiusKm);

    /**
     * 全部分页查询商户，带 x/y 时按距离由近到远排序并返回 distance（公里）。
     *
     * @param radiusKm 只看该半径内的商户，为空表示不限半径
     */
    Result queryNearbyShops(Integer current, Double x, Double y, Double radiusKm);

    /** 关键词搜索商户（名称 / 商圈 / 地址） */
    Result searchShop(String keyword, Integer current);
}
