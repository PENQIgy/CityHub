package com.cityhub.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cityhub.config.CityHubProperties;
import com.cityhub.dto.LocationDTO;
import com.cityhub.service.ILocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.cityhub.utils.RedisConstants.LOCATION_IP_KEY;
import static com.cityhub.utils.RedisConstants.LOCATION_IP_MISS_TTL_HOURS;
import static com.cityhub.utils.RedisConstants.LOCATION_IP_TTL_DAYS;

/**
 * 定位服务实现。
 * <p>
 * 背景：线上的站点是 {@code http://}，Chromium 系浏览器在非安全来源下会直接拒绝
 * {@code navigator.geolocation}，所以「获取当前定位」必须有一条不依赖浏览器授权的通路。
 * 这里用服务端拿到的客户端公网 IP 去查城市级坐标，结果缓存在 Redis 里，
 * 外部接口不可用时返回 null，由前端退化为「手动选城市 / 默认城市」。
 *
 * @author CityHub
 */
@Slf4j
@Service
public class LocationServiceImpl implements ILocationService {

    /** 缓存里表示「这个 IP 查不到」的占位值，避免反复打外部接口 */
    private static final String MISS = "__MISS__";

    /** 只允许合法 IP 字符，防止把 Header 里的脏数据拼进 URL */
    private static final Pattern IP_PATTERN = Pattern.compile("^[0-9a-fA-F:.]{3,45}$");

    /**
     * 内置城市中心点表：城市名 -> 经纬度。
     * <p>
     * 说明：坐标取城市中心附近，精度到「城市级」足够支撑「附近商户」的距离排序；
     * 也是前端「手动选择城市」下拉框的数据源。
     */
    private static final Map<String, double[]> CITY_CENTERS = new LinkedHashMap<>();

    /** 城市别名（IP 库返回的写法 -> 内置城市名） */
    private static final Map<String, String> CITY_ALIAS = new LinkedHashMap<>();

    static {
        putCity("杭州", 120.149192, 30.316078);
        putCity("北京", 116.407400, 39.904200);
        putCity("上海", 121.473700, 31.230400);
        putCity("广州", 113.264400, 23.129100);
        putCity("深圳", 114.057900, 22.543100);
        putCity("成都", 104.066500, 30.572300);
        putCity("重庆", 106.551600, 29.563000);
        putCity("武汉", 114.305500, 30.592800);
        putCity("西安", 108.939800, 34.341600);
        putCity("南京", 118.796900, 32.060300);
        putCity("天津", 117.201000, 39.084200);
        putCity("苏州", 120.595400, 31.298900);
        putCity("长沙", 112.938800, 28.228200);
        putCity("郑州", 113.625400, 34.746600);
        putCity("青岛", 120.382600, 36.067100);
        putCity("宁波", 121.544000, 29.868300);
        putCity("厦门", 118.089400, 24.479800);
        putCity("福州", 119.296500, 26.074500);
        putCity("合肥", 117.227200, 31.820600);
        putCity("济南", 117.120500, 36.651000);
        putCity("沈阳", 123.431500, 41.805700);
        putCity("大连", 121.614700, 38.914000);
        putCity("哈尔滨", 126.534900, 45.803800);
        putCity("昆明", 102.832900, 24.880100);
        putCity("贵阳", 106.630200, 26.647700);
        putCity("南宁", 108.366500, 22.817000);
        putCity("太原", 112.548900, 37.870600);
        putCity("石家庄", 114.514900, 38.042800);
        putCity("南昌", 115.858100, 28.682000);
        putCity("兰州", 103.834300, 36.061100);
        putCity("乌鲁木齐", 87.616800, 43.825600);
        putCity("呼和浩特", 111.751900, 40.841400);
        putCity("银川", 106.230900, 38.487200);
        putCity("西宁", 101.778200, 36.617100);
        putCity("海口", 110.199900, 20.044400);
        putCity("三亚", 109.511900, 18.252800);
        putCity("拉萨", 91.140900, 29.645600);
        putCity("无锡", 120.311900, 31.491200);
        putCity("温州", 120.699400, 27.994400);
        putCity("佛山", 113.122000, 23.028800);
        putCity("东莞", 113.751800, 23.020900);
        putCity("珠海", 113.576700, 22.270700);
        putCity("泉州", 118.675700, 24.874100);
        putCity("徐州", 117.184800, 34.261800);
        putCity("常州", 119.974100, 31.811200);
        putCity("嘉兴", 120.755500, 30.746100);
        putCity("绍兴", 120.580200, 30.030300);
        putCity("金华", 119.649700, 29.078800);
        putCity("台州", 121.428800, 28.661300);
        putCity("湖州", 120.086700, 30.894300);
        putCity("舟山", 122.207200, 29.985300);

        alias("杭州市", "杭州");
        alias("北京市", "北京");
        alias("上海市", "上海");
        alias("广州市", "广州");
        alias("深圳市", "深圳");
        alias("成都市", "成都");
        alias("重庆市", "重庆");
        alias("武汉市", "武汉");
        alias("西安市", "西安");
        alias("南京市", "南京");
        alias("天津市", "天津");
        alias("苏州市", "苏州");
        alias("长沙市", "长沙");
        alias("郑州市", "郑州");
        alias("青岛市", "青岛");
        alias("宁波市", "宁波");
        alias("厦门市", "厦门");
        alias("福州市", "福州");
        alias("合肥市", "合肥");
        alias("济南市", "济南");
        alias("沈阳市", "沈阳");
        alias("大连市", "大连");
        alias("哈尔滨市", "哈尔滨");
        alias("昆明市", "昆明");
        alias("贵阳市", "贵阳");
        alias("南宁市", "南宁");
        alias("太原市", "太原");
        alias("石家庄市", "石家庄");
        alias("南昌市", "南昌");
        alias("兰州市", "兰州");
        alias("乌鲁木齐市", "乌鲁木齐");
        alias("呼和浩特市", "呼和浩特");
        alias("银川市", "银川");
        alias("西宁市", "西宁");
        alias("海口市", "海口");
        alias("三亚市", "三亚");
        alias("拉萨市", "拉萨");
        alias("无锡市", "无锡");
        alias("温州市", "温州");
        alias("佛山市", "佛山");
        alias("东莞市", "东莞");
        alias("珠海市", "珠海");
        alias("泉州市", "泉州");
        alias("徐州市", "徐州");
        alias("常州市", "常州");
        alias("嘉兴市", "嘉兴");
        alias("绍兴市", "绍兴");
        alias("金华市", "金华");
        alias("台州市", "台州");
        alias("湖州市", "湖州");
        alias("舟山市", "舟山");
        // 浙江省 / 江苏省等省级兜底
        alias("浙江", "杭州");
        alias("浙江省", "杭州");
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CityHubProperties props;

    private static void putCity(String name, double x, double y) {
        CITY_CENTERS.put(name, new double[]{x, y});
    }

    private static void alias(String from, String to) {
        CITY_ALIAS.put(from, to);
    }

    /* ==================================================================
     *  IP 定位
     * ================================================================== */

    @Override
    public LocationDTO resolveByIp(String ip) {
        if (!isPublicIp(ip)) {
            return null;
        }
        String key = LOCATION_IP_KEY + ip;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (MISS.equals(cached)) {
                return null;
            }
            if (StrUtil.isNotBlank(cached)) {
                return JSONUtil.toBean(cached, LocationDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取 IP 定位缓存失败, ip={}", ip);
        }

        LocationDTO dto = fetchFromProviders(ip);
        try {
            if (dto == null) {
                stringRedisTemplate.opsForValue()
                        .set(key, MISS, LOCATION_IP_MISS_TTL_HOURS, TimeUnit.HOURS);
            } else {
                stringRedisTemplate.opsForValue()
                        .set(key, JSONUtil.toJsonStr(dto), LOCATION_IP_TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.warn("写入 IP 定位缓存失败, ip={}", ip);
        }
        return dto;
    }

    /**
     * 依次尝试多个免费 IP 库，任意一个成功就返回。
     * <p>
     * 之所以要多个：境外接口（ip-api / ip.sb）在国内服务器上偶发不可达，
     * 太平洋电脑网的接口在国内稳定但只给省市名，需要用内置城市表换算坐标。
     */
    private LocationDTO fetchFromProviders(String ip) {
        long deadline = System.currentTimeMillis() + Math.max(3000, props.getIpLocationTimeoutMs() * 3L);
        LocationDTO dto = fromIpApi(ip);
        if (dto == null && System.currentTimeMillis() < deadline) {
            dto = fromIpSb(ip);
        }
        if (dto == null && System.currentTimeMillis() < deadline) {
            dto = fromPconline(ip);
        }
        if (dto == null) {
            log.info("IP 定位失败，前端将退化为手动 / 默认城市, ip={}", ip);
        }
        return dto;
    }

    /** ip-api.com：直接给经纬度（免费版只支持 http） */
    private LocationDTO fromIpApi(String ip) {
        String url = "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,message,country,regionName,city,lat,lon";
        try {
            String body = HttpUtil.get(url, props.getIpLocationTimeoutMs());
            if (StrUtil.isBlank(body)) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(body);
            if (!"success".equalsIgnoreCase(json.getStr("status"))) {
                return null;
            }
            Double lat = Convert.toDouble(json.get("lat"), null);
            Double lon = Convert.toDouble(json.get("lon"), null);
            if (lat == null || lon == null) {
                return null;
            }
            return build(lon, lat, json.getStr("regionName"), json.getStr("city"), "ip");
        } catch (Exception e) {
            log.debug("ip-api 定位失败, ip={}, cause={}", ip, e.toString());
            return null;
        }
    }

    /** api.ip.sb：直接给经纬度 */
    private LocationDTO fromIpSb(String ip) {
        String url = "https://api.ip.sb/geoip/" + ip;
        try {
            String body = HttpUtil.get(url, props.getIpLocationTimeoutMs());
            if (StrUtil.isBlank(body)) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(body);
            Double lat = Convert.toDouble(json.get("latitude"), null);
            Double lon = Convert.toDouble(json.get("longitude"), null);
            if (lat == null || lon == null) {
                return null;
            }
            return build(lon, lat, json.getStr("region"), json.getStr("city"), "ip");
        } catch (Exception e) {
            log.debug("ip.sb 定位失败, ip={}, cause={}", ip, e.toString());
            return null;
        }
    }

    /** 太平洋电脑网：只给省市名，用内置城市表换算坐标 */
    private LocationDTO fromPconline(String ip) {
        String url = "https://whois.pconline.com.cn/ipJson.jsp?ip=" + ip + "&json=true";
        try {
            String body = HttpRequest.get(url)
                    .charset(CharsetUtil.CHARSET_GBK)
                    .timeout(props.getIpLocationTimeoutMs())
                    .execute()
                    .body();
            if (StrUtil.isBlank(body)) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(body);
            String city = StrUtil.blankToDefault(json.getStr("city"), json.getStr("pro"));
            LocationDTO center = cityCenter(city);
            if (center == null) {
                return null;
            }
            center.setSource("ip");
            center.setAccuracy("城市级");
            return center;
        } catch (Exception e) {
            log.debug("pconline 定位失败, ip={}, cause={}", ip, e.toString());
            return null;
        }
    }

    private LocationDTO build(Double x, Double y, String region, String city, String source) {
        String label;
        if (StrUtil.isNotBlank(region) && StrUtil.isNotBlank(city) && !region.equals(city)) {
            label = region + " · " + city;
        } else if (StrUtil.isNotBlank(city)) {
            label = city;
        } else {
            label = StrUtil.blankToDefault(region, "未知位置");
        }
        return new LocationDTO(x, y, label, source, "城市级");
    }

    /* ==================================================================
     *  城市表
     * ================================================================== */

    @Override
    public List<Map<String, Object>> listCities() {
        List<Map<String, Object>> list = new ArrayList<>(CITY_CENTERS.size());
        CITY_CENTERS.forEach((name, point) -> {
            Map<String, Object> item = new LinkedHashMap<>(4);
            item.put("name", name);
            item.put("x", point[0]);
            item.put("y", point[1]);
            list.add(item);
        });
        return list;
    }

    @Override
    public LocationDTO cityCenter(String cityName) {
        if (StrUtil.isBlank(cityName)) {
            return null;
        }
        String name = cityName.trim();
        String mapped = CITY_ALIAS.getOrDefault(name, name);
        double[] point = CITY_CENTERS.get(mapped);
        if (point == null) {
            // IP 库可能返回「浙江省杭州市」这种全称，做一次包含匹配
            for (Map.Entry<String, double[]> entry : CITY_CENTERS.entrySet()) {
                if (name.contains(entry.getKey())) {
                    point = entry.getValue();
                    mapped = entry.getKey();
                    break;
                }
            }
        }
        if (point == null) {
            return null;
        }
        return new LocationDTO(point[0], point[1], mapped, "city", "城市级");
    }

    /* ==================================================================
     *  工具
     * ================================================================== */

    /**
     * 是否是可用的公网 IP。
     * <p>
     * 内网 / 回环 / 链路本地地址查不出城市，直接跳过；
     * 同时用正则限制字符集，避免 Header 里的脏数据被拼进请求 URL。
     */
    public static boolean isPublicIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        String value = ip.trim();
        if (!IP_PATTERN.matcher(value).matches()) {
            return false;
        }
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        if (lower.startsWith("fe80:") || lower.startsWith("fc") || lower.startsWith("fd")
                || lower.startsWith("::ffff:127.") || lower.startsWith("::ffff:10.")
                || lower.startsWith("::ffff:192.168.")) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            // 其它 IPv6 交给外部接口去判断
            return true;
        }
        if (value.startsWith("127.") || value.startsWith("10.") || value.startsWith("192.168.")
                || value.startsWith("169.254.") || value.startsWith("0.")) {
            return false;
        }
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        if (value.startsWith("100.")) {
            String[] parts = value.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 64 && second <= 127) {
                        return false;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return true;
    }
}
