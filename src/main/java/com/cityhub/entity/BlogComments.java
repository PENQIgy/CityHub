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
 * 探店笔记评论
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 用户id */
    private Long userId;
    /** 探店id */
    private Long blogId;
    /** 关联的1级评论id，如果是一级评论，则值为0 */
    private Long parentId;
    /** 回复的评论id */
    private Long answerId;
    /** 回复的内容 */
    private String content;
    /** 评论图片，多张以","隔开 */
    private String images;
    /** 点赞数 */
    private Integer liked;
    /** 状态，0：正常，1：被举报，2：禁止查看 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;

    /* ------------------------------------------------------------------
     * 以下为展示用的非数据库字段
     * ------------------------------------------------------------------ */

    /** 评论者昵称 */
    @TableField(exist = false)
    private String name;
    /** 评论者头像 */
    @TableField(exist = false)
    private String icon;
    /** 当前登录用户是否点过赞 */
    @TableField(exist = false)
    private Boolean isLike;
    /** 被回复人昵称 */
    @TableField(exist = false)
    private String answerName;
    /** 二级回复 */
    @TableField(exist = false)
    private List<BlogComments> children;
    /** 该一级评论下的回复总数 */
    @TableField(exist = false)
    private Integer replyCount;
    /** 评论作者是否是当前笔记的作者 */
    @TableField(exist = false)
    private Boolean isAuthor;
}
