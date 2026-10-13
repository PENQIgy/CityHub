package com.cityhub.controller;

import cn.hutool.core.bean.BeanUtil;
import com.cityhub.dto.LoginFormDTO;
import com.cityhub.dto.Result;
import com.cityhub.dto.UserDTO;
import com.cityhub.dto.UserProfileDTO;
import com.cityhub.entity.User;
import com.cityhub.entity.UserInfo;
import com.cityhub.service.IUserInfoService;
import com.cityhub.service.IUserService;
import com.cityhub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * 用户前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码。
     * <p>项目没有接入真实短信通道，为了方便联调把验证码一并返回。</p>
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /** 登录 */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        return userService.login(loginForm, session);
    }

    /** 登出 */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        return userService.logout(token);
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public Result me() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /** 用户详情（tb_user_info） */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    /** 用户主页聚合信息：昵称/头像 + 资料 + 统计 */
    @GetMapping("/profile/{id}")
    public Result profile(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("nickName", user.getNickName());
        data.put("icon", user.getIcon());
        UserInfo info = userInfoService.getById(userId);
        if (info != null) {
            data.put("city", info.getCity());
            data.put("introduce", info.getIntroduce());
            data.put("gender", info.getGender());
        }
        Result stats = userService.queryUserStats(userId);
        data.put("stats", stats.getData());
        return Result.ok(data);
    }

    /** 用户统计：笔记 / 关注 / 粉丝 / 获赞 */
    @GetMapping("/stats/{id}")
    public Result stats(@PathVariable("id") Long userId) {
        return userService.queryUserStats(userId);
    }

    /** 更新个人资料 */
    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserProfileDTO profile) {
        return userService.updateUserInfo(profile);
    }

    /** 兼容旧前端：按 id 查询脱敏后的用户信息 */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }
}
