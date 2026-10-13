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

    public String getImageUploadDir() {
        if (imageUploadDir == null || imageUploadDir.isEmpty()) {
            return "D:\\nginx-1.18.0\\html\\cityhub\\imgs\\";
        }
        return imageUploadDir.endsWith("\\") || imageUploadDir.endsWith("/")
                ? imageUploadDir
                : imageUploadDir + "\\";
    }
}
