package com.cityhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.dto.Result;
import com.cityhub.entity.Topic;
import com.cityhub.mapper.TopicMapper;
import com.cityhub.service.ITopicService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 话题服务实现
 * </p>
 *
 * @author CityHub
 */
@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements ITopicService {

    @Override
    public Result queryTopics() {
        List<Topic> topics = query()
                .eq("status", 0)
                .orderByAsc("sort")
                .orderByDesc("blog_count")
                .list();
        return Result.ok(topics);
    }

    @Override
    public Result queryHotTopics(Integer limit) {
        int size = (limit == null || limit < 1) ? 10 : limit;
        List<Topic> topics = query()
                .eq("status", 0)
                .orderByDesc("blog_count")
                .orderByDesc("view_count")
                .last("LIMIT " + size)
                .list();
        return Result.ok(topics);
    }

    @Override
    public Result queryTopicById(Long id) {
        Topic topic = getById(id);
        if (topic == null) {
            return Result.fail("话题不存在");
        }
        return Result.ok(topic);
    }
}
