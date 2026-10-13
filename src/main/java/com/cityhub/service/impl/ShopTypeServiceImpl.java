package com.cityhub.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.cityhub.dto.Result;
import com.cityhub.entity.ShopType;
import com.cityhub.mapper.ShopTypeMapper;
import com.cityhub.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result cacheSelect() {
        String typeListJson = stringRedisTemplate.opsForValue().get("cache:shop-type:list");
        if (StrUtil.isNotBlank(typeListJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(typeListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }

        List<ShopType> shopTypeList = list();
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            return Result.fail("商铺类型不存在");
        }
        // 分类是低频变更的字典数据，缓存 1 小时
        stringRedisTemplate.opsForValue()
                .set("cache:shop-type:list", JSONUtil.toJsonStr(shopTypeList), 1, java.util.concurrent.TimeUnit.HOURS);
        return Result.ok(shopTypeList);
    }
}