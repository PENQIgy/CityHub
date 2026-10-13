package com.cityhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.config.CityHubProperties;
import com.cityhub.dto.Result;
import com.cityhub.dto.UserDTO;
import com.cityhub.entity.Follow;
import com.cityhub.entity.User;
import com.cityhub.entity.UserInfo;
import com.cityhub.mapper.FollowMapper;
import com.cityhub.service.IFollowService;
import com.cityhub.service.IUserInfoService;
import com.cityhub.service.IUserService;
import com.cityhub.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cityhub.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 * 用户关注服务实现
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private CityHubProperties props;

    @Override
    public Result follow(long followUserId, boolean isFollow) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (Objects.equals(userId, followUserId)) {
            return Result.fail("不能关注自己");
        }
        if (userService.getById(followUserId) == null) {
            return Result.fail("用户不存在");
        }
        String key = FOLLOW_KEY + userId;
        if (isFollow) {
            int exists = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
            if (exists == 0) {
                Follow follow = new Follow().setUserId(userId).setFollowUserId(followUserId);
                if (!save(follow)) {
                    return Result.fail("关注失败");
                }
                stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
            }
        } else {
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
        }
        syncCounts(followUserId);
        syncCounts(userId);
        return Result.ok();
    }

    @Override
    public Result isFollow(long followUserId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.ok(false);
        }
        // 修复原实现的 bug：原来用的是全表 count()，会把所有人的关注数算进来
        int count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(long id) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        Set<String> set = stringRedisTemplate.opsForSet()
                .intersect(FOLLOW_KEY + userId, FOLLOW_KEY + id);
        if (set == null || set.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    @Override
    public Result queryFollowList(Long userId, boolean fans, Integer current) {
        if (userId == null || userService.getById(userId) == null) {
            return Result.fail("用户不存在");
        }
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<Follow> page = query()
                .eq(fans ? "follow_user_id" : "user_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(pageNo, props.getBlogPageSize(), false));
        List<Long> ids = page.getRecords().stream()
                .map(f -> fans ? f.getUserId() : f.getFollowUserId())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        Map<Long, User> userMap = userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<Map<String, Object>> vos = new java.util.ArrayList<>(ids.size());
        for (Long id : ids) {
            User user = userMap.get(id);
            if (user == null) {
                continue;
            }
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", user.getId());
            vo.put("nickName", user.getNickName());
            vo.put("icon", user.getIcon());
            UserInfo info = userInfoService.getById(user.getId());
            if (info != null) {
                vo.put("introduce", info.getIntroduce());
                vo.put("city", info.getCity());
            }
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    /**
     * 同步 tb_user_info 上的关注 / 粉丝冗余计数（读接口仍以实时统计为准）。
     */
    private void syncCounts(Long userId) {
        if (userId == null) {
            return;
        }
        int fans = query().eq("follow_user_id", userId).count();
        int followee = query().eq("user_id", userId).count();
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            UserInfo created = new UserInfo()
                    .setUserId(userId)
                    .setCity("杭州")
                    .setFans(fans)
                    .setFollowee(followee)
                    .setBlogCount(0)
                    .setLikedCount(0)
                    .setCredits(0)
                    .setLevel(false)
                    .setGender(false);
            userInfoService.save(created);
        } else {
            userInfoService.update()
                    .set("fans", fans)
                    .set("followee", followee)
                    .eq("user_id", userId)
                    .update();
        }
    }
}
