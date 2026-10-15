package com.cityhub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 定位结果。字段含义与前端 {@code chLoc} 模块保持一致。
 *
 * @author CityHub
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 经度 */
    private Double x;

    /** 纬度 */
    private Double y;

    /** 可展示的城市名，例如「杭州 · 拱墅区」 */
    private String city;

    /** 定位来源：gps / ip / city / default */
    private String source;

    /** 精度描述，例如「城市级」 */
    private String accuracy;
}
