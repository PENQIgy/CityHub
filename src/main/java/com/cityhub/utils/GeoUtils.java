package com.cityhub.utils;

/**
 * 经纬度相关工具。
 * <p>
 * 约定（与 {@code tb_shop.x / tb_shop.y} 保持一致）：
 * <ul>
 *     <li>{@code x} = 经度 longitude，取值范围 [-180, 180]</li>
 *     <li>{@code y} = 纬度 latitude，取值范围 [-90, 90]</li>
 * </ul>
 * Redis GEO 的 {@code Point(x, y)} 也遵循「x=经度、y=纬度」的顺序，两者不可混用。
 *
 * @author CityHub
 */
public class GeoUtils {

    /** 地球平均半径（公里） */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /**
     * 经纬度是否合法（都必须是有限数值，且落在合法区间内）。
     */
    public static boolean isValid(Double x, Double y) {
        if (x == null || y == null) {
            return false;
        }
        if (x.isNaN() || x.isInfinite() || y.isNaN() || y.isInfinite()) {
            return false;
        }
        return x >= -180 && x <= 180 && y >= -90 && y <= 90;
    }

    /**
     * 两组经纬度之间的距离（公里），Haversine 公式。
     * <p>
     * 用于在 Redis GEO 之外兜底计算距离：GEO 索引里可能没有商户
     * （坐标为空的旧数据、或 Redis 被清空），此时靠它保证商户列表依然带距离。
     *
     * @return 距离（公里，保留原始精度）；任一坐标非法时返回 {@code null}
     */
    public static Double distanceKm(Double x1, Double y1, Double x2, Double y2) {
        if (!isValid(x1, y1) || !isValid(x2, y2)) {
            return null;
        }
        double radLat1 = Math.toRadians(y1);
        double radLat2 = Math.toRadians(y2);
        double deltaLat = radLat1 - radLat2;
        double deltaLng = Math.toRadians(x1) - Math.toRadians(x2);
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        double c = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
        return EARTH_RADIUS_KM * c;
    }
}
