package com.english.service;

import com.english.entity.ContentTag;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Set;

public interface ContentTagService extends IService<ContentTag> {

    /** 返回当前所有未删除标签的名称集合（case-insensitive 比较，存原始大小写）。 */
    Set<String> getAllTagNames();

    /**
     * 过滤标签字符串，只保留白名单内的标签。
     * @param rawTags 逗号分隔的标签串（可为 null / 空）
     * @return 合法标签逗号串；若全部非法则返回空字符串
     */
    String filterValidTags(String rawTags);
}
