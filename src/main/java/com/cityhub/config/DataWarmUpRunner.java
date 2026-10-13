package com.cityhub.config;

import com.cityhub.entity.SeckillVoucher;
import com.cityhub.entity.Shop;
import com.cityhub.service.ISeckillVoucherService;
import com.cityhub.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cityhub.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.cityhub.utils.RedisConstants.SHOP_GEO_ALL_KEY;
import static com.cityhub.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 启动预热：把商户坐标写入 Redis GEO 索引、把秒杀券库存写入 Redis。
 * <p>
 * 原来这些数据只能靠手写测试用例加载，导致「附近 Feed 流」和秒杀功能开箱即用不了。
 *
 * @author CityHub
 */
@Slf4j
@Component
public class DataWarmUpRunner implements ApplicationRunner {

    @Resource
    private IShopService shopService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            warmUpShopGeo();
        } catch (Exception e) {
            log.warn("商户 GEO 索引预热失败", e);
        }
        try {
            warmUpSeckillStock();
        } catch (Exception e) {
            log.warn("秒杀库存预热失败", e);
        }
    }

    private void warmUpShopGeo() {
        List<Shop> shops = shopService.list();
        if (shops.isEmpty()) {
            return;
        }
        Long existing = stringRedisTemplate.opsForZSet().zCard(SHOP_GEO_ALL_KEY);
        if (existing != null && existing >= shops.size()) {
            log.info("商户 GEO 索引已就绪，共 {} 个商户", existing);
            return;
        }
        // 全量索引（附近 Feed 流使用）
        List<RedisGeoCommands.GeoLocation<String>> allLocations = new ArrayList<>(shops.size());
        Map<Long, List<Shop>> byType = shops.stream()
                .filter(s -> s.getX() != null && s.getY() != null && s.getTypeId() != null)
                .collect(Collectors.groupingBy(Shop::getTypeId));
        for (Shop shop : shops) {
            if (shop.getX() == null || shop.getY() == null) {
                continue;
            }
            allLocations.add(new RedisGeoCommands.GeoLocation<>(
                    shop.getId().toString(), new Point(shop.getX(), shop.getY())));
        }
        if (!allLocations.isEmpty()) {
            stringRedisTemplate.delete(SHOP_GEO_ALL_KEY);
            stringRedisTemplate.opsForGeo().add(SHOP_GEO_ALL_KEY, allLocations);
        }
        // 按类型分桶（附近商户列表使用）
        byType.forEach((typeId, list) -> {
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(list.size());
            for (Shop shop : list) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            String key = SHOP_GEO_KEY + typeId;
            stringRedisTemplate.delete(key);
            stringRedisTemplate.opsForGeo().add(key, locations);
        });
        log.info("商户 GEO 索引预热完成，共 {} 个商户，{} 个分类", allLocations.size(), byType.size());
    }

    private void warmUpSeckillStock() {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        if (vouchers.isEmpty()) {
            return;
        }
        int loaded = 0;
        for (SeckillVoucher voucher : vouchers) {
            String key = SECKILL_STOCK_KEY + voucher.getVoucherId();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                continue;
            }
            stringRedisTemplate.opsForValue()
                    .set(key, String.valueOf(voucher.getStock() == null ? 0 : voucher.getStock()),
                            7, TimeUnit.DAYS);
            loaded++;
        }
        if (loaded > 0) {
            log.info("秒杀库存预热完成，新增 {} 张券", loaded);
        }
    }
}
