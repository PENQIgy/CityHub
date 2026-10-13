package com.cityhub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.entity.Shop;
import com.cityhub.mapper.ShopMapper;
import com.cityhub.service.IShopService;
import com.cityhub.utils.RedisConstants;
import com.cityhub.utils.SystemConstants;
import com.cityhub.utils.cacheClient;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.cityhub.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 商户服务实现
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    /** 缓存重建线程池，供 cacheClient 逻辑过期方案使用 */
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10, r -> {
        Thread t = new Thread(r, "cityhub-cache-rebuild");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private cacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 互斥锁 / 空值缓存解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺不存在");
        }
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok(shop);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        int pageNo = current == null || current < 1 ? 1 : current;
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(pageNo, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        int from = (pageNo - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = pageNo * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results;
        try {
            results = stringRedisTemplate.opsForGeo().radius(key,
                    new Circle(new Point(x, y), new Distance(5, Metrics.KILOMETERS)),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                            .includeDistance().sortAscending().limit(end));
        } catch (Exception e) {
            log.warn("GEO 查询失败，回退到普通分页, typeId=" + typeId + ", cause=" + e);
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(pageNo, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(results.getContent().size());
        Map<String, Distance> distanceMap = new HashMap<>(results.getContent().size());
        results.getContent().stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, result.getDistance());
        });
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids)
                .last("ORDER BY FIELD(id, " + idStr + ")").list();
        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        return Result.ok(shops);
    }

    @Override
    public Result searchShop(String keyword, Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        if (StrUtil.isBlank(keyword)) {
            Page<Shop> page = query()
                    .orderByDesc("sold")
                    .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
            return Result.ok(page.getRecords(), page.getTotal());
        }
        String kw = keyword.trim();
        Page<Shop> page = query()
                .and(w -> w.like("name", kw).or().like("area", kw).or().like("address", kw))
                .orderByDesc("sold")
                .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords(), page.getTotal());
    }
}
