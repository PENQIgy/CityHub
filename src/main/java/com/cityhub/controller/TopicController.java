package com.cityhub.controller;

import com.cityhub.dto.Result;
import com.cityhub.service.ITopicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * 话题前端控制器
 * </p>
 *
 * @author CityHub
 */
@RestController
@RequestMapping("/topic")
public class TopicController {

    @Resource
    private ITopicService topicService;

    /** 话题列表 */
    @GetMapping("/list")
    public Result queryTopics() {
        return topicService.queryTopics();
    }

    /** 热门话题 */
    @GetMapping("/hot")
    public Result queryHotTopics(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return topicService.queryHotTopics(limit);
    }

    /** 话题详情 */
    @GetMapping("/{id}")
    public Result queryTopicById(@PathVariable("id") Long id) {
        return topicService.queryTopicById(id);
    }
}
