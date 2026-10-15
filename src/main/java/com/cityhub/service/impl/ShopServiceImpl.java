package com.cityhub.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.config.CityHubProperties;
import com.cityhub.dto.Result;
import com.cityhub.entity.Shop;
import com.cityhub.mapper.ShopMapper;
import com.cityhub.service.IShopService;
import com.cityhub.service.IShopTypeService;
import com.cityhub.utils.GeoUtils;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    /** Redis GEO 单次扫描上限，防止极端数据量下把整个索引捞回来 */
    private static final int MAX_GEO_SCAN = 500;

    /** 距离兜底计算的商户扫描上限（超过后会打日志提示） */
    private static final int MAX_SHOP_SCAN = 2000;

    @Resource
    private cacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService shopTypeService;
    @Resource
    private CityHubProperties props;

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
        Shop exist = getById(id);
        if (exist == null) {
            return Result.fail("商铺不存在");
        }
        Long oldTypeId = exist.getTypeId();
        // 只覆盖前端传上来的字段，避免把没传的字段整成 null
        if (StrUtil.isNotBlank(shop.getName())) {
            exist.setName(shop.getName().trim());
        }
        if (shop.getTypeId() != null) {
            exist.setTypeId(shop.getTypeId());
        }
        if (shop.getImages() != null) {
            exist.setImages(shop.getImages());
        }
        if (shop.getArea() != null) {
            exist.setArea(shop.getArea());
        }
        if (StrUtil.isNotBlank(shop.getAddress())) {
            exist.setAddress(shop.getAddress().trim());
        }
        if (shop.getX() != null) {
            exist.setX(shop.getX());
        }
        if (shop.getY() != null) {
            exist.setY(shop.getY());
        }
        if (shop.getAvgPrice() != null) {
            exist.setAvgPrice(shop.getAvgPrice());
        }
        if (shop.getScore() != null) {
            exist.setScore(shop.getScore());
        }
        if (shop.getOpenHours() != null) {
            exist.setOpenHours(shop.getOpenHours());
        }
        Result invalid = validateShop(exist);
        if (invalid != null) {
            return invalid;
        }
        if (!updateById(exist)) {
            return Result.fail("更新商铺失败");
        }
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 分类变了要把旧分类的 GEO 桶清掉，再按新坐标/分类重建索引
        if (!Objects.equals(oldTypeId, exist.getTypeId())) {
            removeShopGeo(exist.getId(), oldTypeId);
        }
        indexShopGeo(exist);
        return Result.ok(exist.getId());
    }

    @Override
    public Result createShop(Shop shop) {
        if (shop == null) {
            return Result.fail("参数错误");
        }
        shop.setId(null)
                .setName(shop.getName() == null ? null : shop.getName().trim())
                .setAddress(shop.getAddress() == null ? null : shop.getAddress().trim())
                .setArea(shop.getArea() == null ? null : shop.getArea().trim())
                .setOpenHours(shop.getOpenHours() == null ? null : shop.getOpenHours().trim())
                .setImages(shop.getImages() == null ? null : shop.getImages().trim());
        Result invalid = validateShop(shop);
        if (invalid != null) {
            return invalid;
        }
        // 新商户的统计字段给默认值，避免前端直接读 null
        if (shop.getSold() == null) {
            shop.setSold(0);
        }
        if (shop.getComments() == null) {
            shop.setComments(0);
        }
        if (shop.getScore() == null) {
            shop.setScore(0);
        }
        shop.setCreateTime(null).setUpdateTime(null);
        if (!save(shop)) {
            return Result.fail("创建商户失败");
        }
        // 关键：写进 Redis GEO 索引，否则「附近」里永远看不到新商户
        indexShopGeo(shop);
        return Result.ok(shop.getId());
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y, Double radiusKm) {
        int pageNo = page(current);
        if (typeId == null) {
            return Result.fail("分类不存在");
        }
        if (!GeoUtils.isValid(x, y)) {
            // 没有定位：按销量倒序普通分页
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .orderByDesc("sold")
                    .orderByDesc("id")
                    .page(new Page<>(pageNo, props.getShopPageSize()));
            return Result.ok(page.getRecords());
        }
        List<Shop> ordered = shopsSortedByDistance((long) typeId, x, y, radiusKm);
        return Result.ok(pageOf(ordered, pageNo, props.getShopPageSize()));
    }

    @Override
    public Result queryNearbyShops(Integer current, Double x, Double y, Double radiusKm) {
        int pageNo = page(current);
        if (!GeoUtils.isValid(x, y)) {
            // 没有定位：按销量倒序，保证列表不空
            Page<Shop> page = query()
                    .orderByDesc("sold")
                    .orderByDesc("id")
                    .page(new Page<>(pageNo, props.getShopPageSize()));
            return Result.ok(page.getRecords());
        }
        List<Shop> ordered = shopsSortedByDistance(null, x, y, radiusKm);
        return Result.ok(pageOf(ordered, pageNo, props.getShopPageSize()));
    }

    @Override
    public Result searchShop(String keyword, Integer current) {
        int pageNo = page(current);
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

    /* ==================================================================
     *  距离排序
     * ================================================================== */

    /**
     * 商户按「离用户由近到远」排序，并给每个商户写上 distance（公里）。
     * <p>
     * 两步走：
     * <ol>
     *     <li>Redis GEO 半径查询拿到最近的商户（已排序、自带距离）—— 走索引，快；</li>
     *     <li>剩下不在 GEO 结果里的商户用 Haversine 补算距离，排在后面。</li>
     * </ol>
     * 半径外的商户距离必然更大，所以「半径内有序 + 剩余按距离升序」= 全局距离升序。
     * 这样即使 Redis 被清空、或商户坐标后来才补上（不在 GEO 索引里），
     * 列表依然完整且每个商户都有距离，不会出现「一片空白」。
     *
     * @param typeId   非空表示只查该分类
     * @param radiusKm 非空表示只返回该半径内的商户（前端「只看 5 公里」这类开关）
     */
    private List<Shop> shopsSortedByDistance(Long typeId, Double x, Double y, Double radiusKm) {
        boolean limited = radiusKm != null && radiusKm > 0;
        double radius = limited ? radiusKm : props.getNearbyRadiusKm();
        List<Shop> ordered = new ArrayList<>();
        Map<Long, Double> seen = new HashMap<>();
        collectFromGeo(typeId, x, y, radius, ordered, seen);

        List<Shop> rest = scanShops(typeId);
        List<Shop> restWithDistance = new ArrayList<>(rest.size());
        for (Shop shop : rest) {
            if (seen.containsKey(shop.getId())) {
                continue;
            }
            Double distance = GeoUtils.distanceKm(x, y, shop.getX(), shop.getY());
            if (limited && distance != null && distance > radius) {
                continue;
            }
            shop.setDistance(distance);
            restWithDistance.add(shop);
        }
        // 距离升序；没有坐标的排在最后，同距离按销量、id 兜底，保证分页稳定
        restWithDistance.sort((a, b) -> {
            Double da = a.getDistance();
            Double db = b.getDistance();
            if (da == null && db != null) {
                return 1;
            }
            if (da != null && db == null) {
                return -1;
            }
            if (da != null) {
                int byDistance = Double.compare(da, db);
                if (byDistance != 0) {
                    return byDistance;
                }
            }
            int soldA = a.getSold() == null ? 0 : a.getSold();
            int soldB = b.getSold() == null ? 0 : b.getSold();
            if (soldA != soldB) {
                return Integer.compare(soldB, soldA);
            }
            long idA = a.getId() == null ? 0L : a.getId();
            long idB = b.getId() == null ? 0L : b.getId();
            return Long.compare(idB, idA);
        });
        ordered.addAll(restWithDistance);
        return ordered;
    }

    /**
     * 从 Redis GEO 取半径内的商户（由近到远），追加到 {@code ordered} 并记录已处理的 id。
     */
    private void collectFromGeo(Long typeId, Double x, Double y, double radiusKm,
                               List<Shop> ordered, Map<Long, Double> seen) {
        String geoKey = typeId == null
                ? RedisConstants.SHOP_GEO_ALL_KEY
                : RedisConstants.SHOP_GEO_KEY + typeId;
        try {
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                    geoKey,
                    new Circle(new Point(x, y), new Distance(radiusKm, Metrics.KILOMETERS)),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                            .includeDistance().sortAscending().limit(MAX_GEO_SCAN));
            if (results == null || results.getContent().isEmpty()) {
                return;
            }
            Map<Long, Double> distances = new LinkedHashMap<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
                try {
                    Long id = Long.valueOf(result.getContent().getName());
                    Double km = result.getDistance() == null ? null : result.getDistance().getValue();
                    distances.put(id, km);
                } catch (NumberFormatException ignored) {
                    // 跳过脏数据
                }
            }
            if (distances.isEmpty()) {
                return;
            }
            Map<Long, Shop> shopMap = listByIds(distances.keySet()).stream()
                    .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a));
            for (Map.Entry<Long, Double> entry : distances.entrySet()) {
                Shop shop = shopMap.get(entry.getKey());
                if (shop == null) {
                    // GEO 里的商户已被删除，顺手清掉索引
                    stringRedisTemplate.opsForGeo().remove(geoKey, entry.getKey().toString());
                    continue;
                }
                if (typeId != null && !Objects.equals(shop.getTypeId(), typeId)) {
                    continue;
                }
                shop.setDistance(entry.getValue());
                ordered.add(shop);
                seen.put(shop.getId(), entry.getValue());
            }
        } catch (Exception e) {
            log.warn("GEO 附近商户查询失败，将退化为 Haversine 计算, typeId=" + typeId + ", cause=" + e);
        }
    }

    /**
     * 兜底：把商户从库里捞出来。演示库量级很小（几十~几百），一次性取出后在内存里排序即可。
     */
    private List<Shop> scanShops(Long typeId) {
        List<Shop> shops = query()
                .eq(typeId != null, "type_id", typeId)
                .last("LIMIT " + (MAX_SHOP_SCAN + 1))
                .list();
        if (shops.size() > MAX_SHOP_SCAN) {
            log.warn("商户数量超过 " + MAX_SHOP_SCAN + "，距离排序只覆盖前 " + MAX_SHOP_SCAN
                    + " 条，建议把距离计算完全交给 Redis GEO");
            return new ArrayList<>(shops.subList(0, MAX_SHOP_SCAN));
        }
        return shops;
    }

    /* ==================================================================
     *  GEO 索引维护
     * ================================================================== */

    /**
     * 把商户写入（或更新）到 Redis GEO 索引：全量索引 + 分类索引各一份。
     */
    private void indexShopGeo(Shop shop) {
        if (shop == null || shop.getId() == null || !GeoUtils.isValid(shop.getX(), shop.getY())) {
            return;
        }
        String member = shop.getId().toString();
        Point point = new Point(shop.getX(), shop.getY());
        try {
            stringRedisTemplate.opsForGeo().add(RedisConstants.SHOP_GEO_ALL_KEY, point, member);
            if (shop.getTypeId() != null) {
                stringRedisTemplate.opsForGeo()
                        .add(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(), point, member);
            }
        } catch (Exception e) {
            // GEO 索引只是加速手段，失败不影响主流程（兜底会走 Haversine）
            log.warn("写入商户 GEO 索引失败, shopId=" + shop.getId() + ", cause=" + e);
        }
    }

    /**
     * 把商户从某个分类的 GEO 索引里移除（改分类时清理旧桶）。
     */
    private void removeShopGeo(Long shopId, Long oldTypeId) {
        if (shopId == null || oldTypeId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForGeo()
                    .remove(RedisConstants.SHOP_GEO_KEY + oldTypeId, shopId.toString());
        } catch (Exception e) {
            log.warn("清理商户 GEO 索引失败, shopId=" + shopId + ", cause=" + e);
        }
    }

    /* ==================================================================
     *  内部工具
     * ================================================================== */

    private int page(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    /** 内存分页（深拷贝，避免返回 subList 视图） */
    private <T> List<T> pageOf(List<T> list, int pageNo, int size) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        int from = Math.min((pageNo - 1) * size, list.size());
        int to = Math.min(from + size, list.size());
        return new ArrayList<>(list.subList(from, to));
    }

    /**
     * 商户参数校验（创建与更新共用）。
     */
    private Result validateShop(Shop shop) {
        if (StrUtil.isBlank(shop.getName())) {
            return Result.fail("商户名称不能为空");
        }
        if (shop.getName().length() > 50) {
            return Result.fail("商户名称最多 50 个字");
        }
        if (shop.getTypeId() == null) {
            return Result.fail("请选择商户分类");
        }
        if (shopTypeService.getById(shop.getTypeId()) == null) {
            return Result.fail("商户分类不存在");
        }
        if (StrUtil.isBlank(shop.getAddress())) {
            return Result.fail("商户地址不能为空");
        }
        if (shop.getAddress().length() > 120) {
            return Result.fail("商户地址最多 120 个字");
        }
        if (shop.getArea() != null && shop.getArea().length() > 50) {
            return Result.fail("商圈最多 50 个字");
        }
        if (shop.getOpenHours() != null && shop.getOpenHours().length() > 50) {
            return Result.fail("营业时间最多 50 个字");
        }
        if (shop.getAvgPrice() != null && shop.getAvgPrice() < 0) {
            return Result.fail("人均价格不能为负数");
        }
        if (shop.getScore() != null && (shop.getScore() < 0 || shop.getScore() > 50)) {
            return Result.fail("评分需要在 0~5 之间");
        }
        boolean hasX = shop.getX() != null;
        boolean hasY = shop.getY() != null;
        if (hasX != hasY) {
            return Result.fail("经度和纬度需要同时填写");
        }
        if (hasX && !GeoUtils.isValid(shop.getX(), shop.getY())) {
            return Result.fail("经纬度超出合法范围（经度 -180~180，纬度 -90~90）");
        }
        return null;
    }
}
