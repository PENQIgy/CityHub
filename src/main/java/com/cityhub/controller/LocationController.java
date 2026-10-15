package com.cityhub.controller;

import cn.hutool.core.util.StrUtil;
import com.cityhub.dto.LocationDTO;
import com.cityhub.dto.Result;
import com.cityhub.service.ILocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 定位辅助接口。
 * <p>
 * 前端定位优先级：本地缓存 → 浏览器 GPS（仅 HTTPS / localhost 可用）
 * → 本接口的 IP 城市级定位 → 手动选城市 → 默认城市。
 *
 * @author CityHub
 */
@Slf4j
@RestController
@RequestMapping("/location")
public class LocationController {

    /** 反向代理常见的客户端 IP 请求头，按可信度排序 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP"
    };

    @Resource
    private ILocationService locationService;

    /**
     * 按客户端公网 IP 解析城市级坐标。
     * <p>
     * 解析不出来时返回 {@code data = null}，前端会退化为默认城市，不会报错。
     */
    @GetMapping("/ip")
    public Result queryByIp(HttpServletRequest request) {
        for (String candidate : candidateIps(request)) {
            LocationDTO dto = locationService.resolveByIp(candidate);
            if (dto != null) {
                return Result.ok(dto);
            }
        }
        return Result.ok();
    }

    /**
     * 手动指定城市：按城市名返回中心点坐标（前端「选择城市」用）。
     */
    @GetMapping("/city")
    public Result queryCity(@RequestParam("name") String name) {
        if (StrUtil.isBlank(name)) {
            return Result.fail("请选择城市");
        }
        LocationDTO dto = locationService.cityCenter(name);
        if (dto == null) {
            return Result.fail("暂不支持该城市");
        }
        return Result.ok(dto);
    }

    /**
     * 内置城市列表，供前端「手动选择城市」下拉框使用。
     */
    @GetMapping("/cities")
    public Result queryCities() {
        return Result.ok(locationService.listCities());
    }

    /**
     * 收集候选客户端 IP：代理头里排在前面的优先，最后兜底 remoteAddr。
     */
    private List<String> candidateIps(HttpServletRequest request) {
        List<String> candidates = new ArrayList<>(4);
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StrUtil.isBlank(value) || "unknown".equalsIgnoreCase(value.trim())) {
                continue;
            }
            // X-Forwarded-For 形如 "client, proxy1, proxy2"，第一个才是真实客户端
            for (String part : value.split(",")) {
                String ip = part.trim();
                if (StrUtil.isNotBlank(ip) && !candidates.contains(ip)) {
                    candidates.add(ip);
                }
            }
        }
        String remote = request.getRemoteAddr();
        if (StrUtil.isNotBlank(remote) && !candidates.contains(remote)) {
            candidates.add(remote);
        }
        return candidates;
    }
}
