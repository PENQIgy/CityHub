package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

     Result cacheSelect();
}