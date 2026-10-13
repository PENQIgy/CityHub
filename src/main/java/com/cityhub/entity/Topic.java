package com.cityhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 话题（CityHub 内容聚合单元，类似小红书的 #话题）
 * </p>
 *
 * @author CityHub
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_topic")
public class Topic implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 话题名称，不含 # */
    private String name;
    /** 话题简介 */
    private String description;
    /** 话题封面图 */
    private String cover;
    /** 关联笔记数 */
    private Integer blogCount;
    /** 话题浏览量 */
    private Integer viewCount;
    /** 排序，越小越靠前 */
    private Integer sort;
    /** 状态 0:正常 1:下线 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
