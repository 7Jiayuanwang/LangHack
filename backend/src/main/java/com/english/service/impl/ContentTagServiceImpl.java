package com.english.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.ContentTag;
import com.english.mapper.ContentTagMapper;
import com.english.service.ContentTagService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContentTagServiceImpl extends ServiceImpl<ContentTagMapper, ContentTag> implements ContentTagService {

    @Override
    public Set<String> getAllTagNames() {
        List<ContentTag> tags = lambdaQuery()
                .eq(ContentTag::getDeleted, 0)
                .list();
        return tags.stream()
                .map(t -> t.getName().toLowerCase())
                .collect(Collectors.toSet());
    }

    @Override
    public String filterValidTags(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return "";
        }
        Set<String> whitelist = getAllTagNames();
        return Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty() && whitelist.contains(t.toLowerCase()))
                .collect(Collectors.joining(","));
    }
}
