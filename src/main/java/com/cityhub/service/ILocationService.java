package com.cityhub.service;

import com.cityhub.dto.LocationDTO;

import java.util.List;
import java.util.Map;

/**
 * 定位服务：给前端提供「浏览器拿不到定位」时的兜底方案。
 * <p>
 * 浏览器原生定位（{@code navigator.geolocation}）在非 HTTPS 的来源下会被
 * Chrome / Edge 直接拒绝，而线上站点目前是 {@code http://}。
 * 所以这里用「服务端能看到的客户端公网 IP + 免费 IP 库」补一个城市级定位。
 *
 * @author CityHub
 */
public interface ILocationService {

    /**
     * 按 IP 解析城市级坐标。
     *
     * @param ip 客户端公网 IP
     * @return 解析成功返回定位结果；IP 是内网地址或所有外部接口都不可用时返回 {@code null}
     */
    LocationDTO resolveByIp(String ip);

    /** 内置城市列表（含中心点坐标），供前端「手动选择城市」使用 */
    List<Map<String, Object>> listCities();

    /** 城市中心点坐标，找不到返回 {@code null} */
    LocationDTO cityCenter(String cityName);
}
