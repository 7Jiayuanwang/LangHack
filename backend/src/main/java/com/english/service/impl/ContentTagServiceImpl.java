package com.english.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.ContentTag;
import com.english.mapper.ContentTagMapper;
import com.english.service.ContentTagService;
import org.springframework.stereotype.Service;

@Service
public class ContentTagServiceImpl extends ServiceImpl<ContentTagMapper, ContentTag> implements ContentTagService {
}
