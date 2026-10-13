package com.cityhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cityhub.entity.BlogCollect;
import com.cityhub.mapper.BlogCollectMapper;
import com.cityhub.service.IBlogCollectService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 笔记收藏服务实现
 * </p>
 *
 * @author CityHub
 */
@Service
public class BlogCollectServiceImpl extends ServiceImpl<BlogCollectMapper, BlogCollect>
        implements IBlogCollectService {

}
