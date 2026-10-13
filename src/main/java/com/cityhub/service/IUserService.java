package com.cityhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cityhub.dto.LoginFormDTO;
import com.cityhub.dto.Result;
import com.cityhub.entity.User;
import com.cityhub.dto.UserProfileDTO;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 用户服务类
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    /** 登出：删除 Redis 中的登录态 */
    Result logout(String token);

    /** 更新个人资料 */
    Result updateUserInfo(UserProfileDTO profile);

    /** 用户主页统计：笔记 / 关注 / 粉丝 / 获赞 */
    Result queryUserStats(Long userId);
}
