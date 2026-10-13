package com.cityhub.dto;

import lombok.Data;

/**
 * 用户主页的统计信息。
 *
 * @author CityHub
 */
@Data
public class UserStatsDTO {
    /** 发布的笔记数 */
    private Integer blogCount = 0;
    /** 关注数 */
    private Integer followee = 0;
    /** 粉丝数 */
    private Integer fans = 0;
    /** 获赞总数 */
    private Integer likedCount = 0;
    /** 被收藏总数 */
    private Integer collectedCount = 0;
}
