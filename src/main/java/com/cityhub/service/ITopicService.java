package com.cityhub.service;

import com.cityhub.dto.Result;
import com.cityhub.entity.Topic;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 话题服务类
 * </p>
 *
 * @author CityHub
 */
public interface ITopicService extends IService<Topic> {

    /** 话题列表（按 sort 升序，附带笔记数） */
    Result queryTopics();

    /** 热门话题（按笔记数倒序） */
    Result queryHotTopics(Integer limit);

    /** 话题详情 */
    Result queryTopicById(Long id);
}
