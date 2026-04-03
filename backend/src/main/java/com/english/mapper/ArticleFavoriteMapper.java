package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.ArticleFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章收藏Mapper接口
 */
@Mapper
public interface ArticleFavoriteMapper extends BaseMapper<ArticleFavorite> {
}
