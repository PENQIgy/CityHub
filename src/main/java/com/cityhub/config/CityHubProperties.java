package com.cityhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CityHub 业务配置，对应 application.yaml 中的 cityhub.* 节点。
 *
 * @author CityHub
 */
@Data
@Component
@ConfigurationProperties(prefix = "cityhub")
public class CityHubProperties {

    /** 图片上传根目录，指向 nginx 静态资源目录 */
    private String imageUploadDir = "D:\\nginx-1.18.0\\html\\cityhub\\imgs\\";

    /** Feed 流每页条数 */
    private int feedPageSize = 10;

    /** 笔记列表每页条数 */
    private int blogPageSize = 10;

    /** 商户列表每页条数（/shop/of/type、/shop/nearby） */
    private int shopPageSize = 5;

    /**
     * 「附近」的默认半径（公里）。
     * <p>
     * 只作为 Redis GEO 快速路径的扫描半径：超出半径的商户会退化为
     * Java 端 Haversine 计算距离后追加，因此列表永远带距离、不会为空。
     */
    private double nearbyRadiusKm = 10D;

    /** IP 定位请求外部接口的超时时间（毫秒） */
    private int ipLocationTimeoutMs = 2500;

    public String getImageUploadDir() {
        if (imageUploadDir == null || imageUploadDir.isEmpty()) {
            return "D:\\nginx-1.18.0\\html\\cityhub\\imgs\\";
        }
        return imageUploadDir.endsWith("\\") || imageUploadDir.endsWith("/")
                ? imageUploadDir
                : imageUploadDir + "\\";
    }
}
