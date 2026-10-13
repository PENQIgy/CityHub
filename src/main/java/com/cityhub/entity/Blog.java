package com.cityhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 探店笔记（CityHub 内容核心）
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 商户id，可为空（纯内容笔记） */
    private Long shopId;
    /** 用户id */
    private Long userId;
    /** 标题 */
    private String title;
    /** 探店的照片，最多9张，多张以","隔开 */
    private String images;
    /** 探店的文字描述 */
    private String content;
    /** 点赞数量 */
    private Integer liked;
    /** 评论数量 */
    private Integer comments;
    /** 浏览量 */
    private Integer viewCount;
    /** 收藏数量 */
    private Integer collected;
    /** 探店评分 1~5 分乘 10 保存 */
    private Integer score;
    /** 话题id */
    private Long topicId;
    /** 定位/商圈 */
    private String location;
    /** 状态 0:正常 1:作者隐藏 2:违规下架 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /* ------------------------------------------------------------------
     * 以下为展示用的非数据库字段
     * ------------------------------------------------------------------ */

    /** 作者头像 */
    @TableField(exist = false)
    private String icon;
    /** 作者昵称 */
    @TableField(exist = false)
    private String name;
    /** 当前登录用户是否点过赞 */
    @TableField(exist = false)
    private Boolean isLike;
    /** 当前登录用户是否收藏过 */
    @TableField(exist = false)
    private Boolean isCollected;
    /** 话题名称 */
    @TableField(exist = false)
    private String topicName;
    /** 关联商户名称 */
    @TableField(exist = false)
    private String shopName;
    /** 关联商户商圈 */
    @TableField(exist = false)
    private String shopArea;
    /** 图片列表（便于前端直接使用） */
    @TableField(exist = false)
    private List<String> imageList;
    /** 首图（列表卡片使用） */
    @TableField(exist = false)
    private String cover;

    /** 把 images 拆成列表，并给出 cover */
    public void splitImages() {
        this.cover = null;
        if (this.images == null || this.images.isEmpty()) {
            this.imageList = java.util.Collections.emptyList();
            return;
        }
        this.imageList = java.util.Arrays.stream(this.images.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
        if (!this.imageList.isEmpty()) {
            this.cover = this.imageList.get(0);
        }
    }
}
