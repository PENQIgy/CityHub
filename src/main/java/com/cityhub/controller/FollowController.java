package com.cityhub.controller;

import com.cityhub.dto.Result;
import com.cityhub.service.IFollowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 用户关注前端控制器
 * </p>
 *
 * @author CityHub
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") long followUserId,
                         @PathVariable("isFollow") boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") long id) {
        return followService.followCommons(id);
    }

    /** 我关注的人 */
    @GetMapping("/list/{id}")
    public Result queryFollowList(@PathVariable("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFollowList(id, false, current);
    }

    /** 我的粉丝 */
    @GetMapping("/fans/{id}")
    public Result queryFansList(@PathVariable("id") Long id,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFollowList(id, true, current);
    }
}