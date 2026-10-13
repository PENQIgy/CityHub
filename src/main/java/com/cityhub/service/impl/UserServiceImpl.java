package com.cityhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.LoginFormDTO;
import com.cityhub.dto.Result;
import com.cityhub.dto.UserDTO;
import com.cityhub.dto.UserProfileDTO;
import com.cityhub.dto.UserStatsDTO;
import com.cityhub.entity.Blog;
import com.cityhub.entity.BlogCollect;
import com.cityhub.entity.Follow;
import com.cityhub.entity.User;
import com.cityhub.entity.UserInfo;
import com.cityhub.mapper.BlogCollectMapper;
import com.cityhub.mapper.BlogMapper;
import com.cityhub.mapper.FollowMapper;
import com.cityhub.mapper.UserMapper;
import com.cityhub.service.IUserInfoService;
import com.cityhub.service.IUserService;
import com.cityhub.utils.RegexUtils;
import com.cityhub.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cityhub.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.cityhub.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.cityhub.utils.RedisConstants.LOGIN_USER_KEY;
import static com.cityhub.utils.RedisConstants.LOGIN_USER_TTL;
import static com.cityhub.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private BlogMapper blogMapper;
    @Resource
    private BlogCollectMapper blogCollectMapper;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送验证码成功，验证码：{}", code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 确保用户详情行存在
        ensureUserInfo(user.getId());

        // 1. 随机生成 token
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 2. 转成 Map，并把所有 value 转成 String（StringRedisTemplate 的 hash 要求）
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue == null ? null : fieldValue.toString()));
        // 3. 存入 Redis
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        }
        UserHolder.removeUser();
        return Result.ok();
    }

    @Override
    @Transactional
    public Result updateUserInfo(UserProfileDTO profile) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (profile == null) {
            return Result.fail("参数错误");
        }
        if (profile.getIntroduce() != null && profile.getIntroduce().length() > 128) {
            return Result.fail("个人简介最多 128 个字符");
        }
        // 昵称与头像落在 tb_user 上
        User user = getById(userId);
        if (user != null) {
            boolean changed = false;
            if (StrUtil.isNotBlank(profile.getNickName())) {
                user.setNickName(profile.getNickName().trim());
                changed = true;
            }
            if (profile.getIcon() != null) {
                user.setIcon(profile.getIcon());
                changed = true;
            }
            if (changed) {
                updateById(user);
                // 同步登录态中的昵称/头像
                refreshLoginCache(userId, user);
            }
        }
        // 其余字段落在 tb_user_info
        ensureUserInfo(userId);
        UserInfo update = new UserInfo().setUserId(userId)
                .setCity(profile.getCity())
                .setIntroduce(profile.getIntroduce())
                .setGender(profile.getGender())
                .setBirthday(profile.getBirthday());
        userInfoService.updateById(update);
        return Result.ok();
    }

    @Override
    public Result queryUserStats(Long userId) {
        if (userId == null || getById(userId) == null) {
            return Result.fail("用户不存在");
        }
        UserStatsDTO stats = new UserStatsDTO();
        stats.setBlogCount(blogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Blog>()
                        .eq("user_id", userId).eq("status", 0)).intValue());
        stats.setFollowee(followMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Follow>()
                        .eq("user_id", userId)).intValue());
        stats.setFans(followMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Follow>()
                        .eq("follow_user_id", userId)).intValue());

        List<Blog> blogs = blogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Blog>()
                        .eq("user_id", userId).eq("status", 0));
        int liked = 0;
        int collected = 0;
        for (Blog blog : blogs) {
            liked += blog.getLiked() == null ? 0 : blog.getLiked();
            collected += blog.getCollected() == null ? 0 : blog.getCollected();
        }
        stats.setLikedCount(liked);
        stats.setCollectedCount(collected);

        // 同步一份冗余计数，便于直接用 SQL 查看
        UserInfo info = userInfoService.getById(userId);
        if (info != null) {
            userInfoService.update()
                    .set("blog_count", stats.getBlogCount())
                    .set("liked_count", stats.getLikedCount())
                    .set("fans", stats.getFans())
                    .set("followee", stats.getFollowee())
                    .eq("user_id", userId).update();
        }
        return Result.ok(stats);
    }

    private void ensureUserInfo(Long userId) {
        if (userId == null || userInfoService.getById(userId) != null) {
            return;
        }
        UserInfo info = new UserInfo()
                .setUserId(userId)
                .setCity("杭州")
                .setIntroduce(null)
                .setFans(0)
                .setFollowee(0)
                .setBlogCount(0)
                .setLikedCount(0)
                .setCredits(0)
                .setGender(false)
                .setLevel(false);
        userInfoService.save(info);
    }

    /**
     * 用户改了昵称/头像后，把 Redis 中该用户所有登录态的 hash 刷新一遍。
     */
    private void refreshLoginCache(Long userId, User user) {
        try {
            java.util.Set<String> keys = stringRedisTemplate.keys(LOGIN_USER_KEY + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            for (String key : keys) {
                Object idVal = stringRedisTemplate.opsForHash().get(key, "id");
                if (idVal != null && Objects.equals(String.valueOf(userId), String.valueOf(idVal))) {
                    Map<String, String> patch = new HashMap<>(2);
                    patch.put("nickName", user.getNickName());
                    patch.put("icon", user.getIcon() == null ? "" : user.getIcon());
                    stringRedisTemplate.opsForHash().putAll(key, patch);
                }
            }
        } catch (Exception e) {
            log.warn("刷新登录态缓存失败, userId={}", userId, e);
        }
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        user.setIcon("");
        save(user);
        return user;
    }
}
