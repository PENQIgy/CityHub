package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户关注服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /** 关注 / 取关 */
    Result follow(long followUserId, boolean follow);

    /** 当前登录用户是否关注了某人 */
    Result isFollow(long followUserId);

    /** 我与某人的共同关注 */
    Result followCommons(long id);

    /**
     * 某人的关注 / 粉丝列表。
     *
     * @param userId  目标用户
     * @param fans    true 表示粉丝列表，false 表示关注列表
     * @param current 页码
     */
    Result queryFollowList(Long userId, boolean fans, Integer current);
}
