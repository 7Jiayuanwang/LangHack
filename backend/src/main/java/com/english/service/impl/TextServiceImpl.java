package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Text;
import com.english.dto.ArticleFilterDTO;
import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.english.service.TextService;
import org.springframework.stereotype.Service;
import com.english.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import com.english.datasource.UseMaster;
import com.english.service.UnderlineService;
import com.english.mapper.TextExerciseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class TextServiceImpl extends ServiceImpl<TextMapper, Text> implements TextService {

    @Autowired
    private UnderlineService underlineService;

    @Autowired
    private TextExerciseMapper textExerciseMapper;

    @Autowired
    private TextUnitMapper textUnitMapper;

    @Override
    @UseMaster
    public Map<String, Object> getTextsByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        Object keywordObj = params.get("keyword");
        String keyword = keywordObj != null ? keywordObj.toString() : null;
        String difficulty = (String) params.get("difficulty");
        Long unitId = params.get("unitId") != null ? Long.valueOf(params.get("unitId").toString()) : null;
        String tag = (String) params.get("tag");
        Integer featured = params.get("featured") != null ? ((Number) params.get("featured")).intValue() : null;
        String articleType = params.get("articleType") != null ? params.get("articleType").toString() : null;
        String bookVolume = params.get("bookVolume") != null ? params.get("bookVolume").toString() : null;
        Integer unitNum = params.get("unitNum") != null ? ((Number) params.get("unitNum")).intValue() : null;

        Page<Text> pageParam = new Page<>(page, size);
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", keyword)
                .or()
                .like("content", keyword)
            );
        }

        if (difficulty != null && !difficulty.trim().isEmpty()) {
            queryWrapper.eq("difficulty", difficulty);
        }

        if (tag != null && !tag.trim().isEmpty()) {
            String[] tags = tag.split(",");
            for (String t : tags) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    queryWrapper.like("tags", trimmed);
                }
            }
        }

        if (featured != null) {
            queryWrapper.eq("is_featured", featured);
        }

        // 课内/课外分类筛选
        if (articleType != null && !articleType.trim().isEmpty()) {
            queryWrapper.eq("article_type", articleType);
        }
        if (bookVolume != null && !bookVolume.trim().isEmpty()) {
            queryWrapper.eq("book_volume", bookVolume);
        }
        if (unitNum != null) {
            queryWrapper.eq("unit_num", unitNum);
        }

        // 如果指定了unitId，则按unitId过滤；否则显示所有课文（包括未分配的）
        if (unitId != null) {
            queryWrapper.and(wrapper -> wrapper
                .eq("unit_id", unitId)
                .or()
                .inSql("id", "SELECT text_id FROM text_units WHERE unit_id = " + unitId));
        }

        queryWrapper.eq("deleted", 0);
        queryWrapper.eq("status", "APPROVED");
        queryWrapper.orderByDesc("created_time");

        IPage<Text> pageResult = page(pageParam, queryWrapper);

        pageResult.getRecords().forEach(text -> {
            List<Long> unitIds = textUnitMapper.selectUnitIdsByTextId(text.getId());
            text.setUnitIds(unitIds);
            if (text.getUnitId() == null && !unitIds.isEmpty()) {
                text.setUnitId(unitIds.get(0));
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    @UseMaster
    public Text getTextById(Long id) {
//        return getById(id);
         QueryWrapper<Text> wrapper = new QueryWrapper<>();
         wrapper.eq("id", id).eq("deleted", 0);
         return getOne(wrapper);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"texts", "publicTexts"}, allEntries = true)
    public void createText(Text text) {
        if (text.getDifficulty() == null) {
            text.setDifficulty("MEDIUM");
        }
        text.setCreatedAt(LocalDateTime.now());
        text.setUpdatedAt(LocalDateTime.now());
        text.setDeleted(0);
        save(text);
        if (text.getId() != null && text.getUnitId() != null && text.getUnitId() > 0) {
            textUnitMapper.insertRelation(text.getId(), text.getUnitId(), text.getOrderNum() == null ? 0 : text.getOrderNum());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"texts", "publicTexts"}, allEntries = true)
    public void updateText(Text text) {
        if (text == null || text.getId() == null) {
            throw new BusinessException("课文对象及ID不能为空");
        }

        // 先检查记录是否存在且未删除
        Text existing = getTextById(text.getId());
        if (existing == null) {
            throw new BusinessException("课文不存在或已被删除");
        }

        text.setUpdatedAt(LocalDateTime.now());

        // 使用UpdateWrapper来强制更新null值，包括所有字段
        UpdateWrapper<Text> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", text.getId());

        // 设置所有需要更新的字段
        if (text.getUnitId() != null) {
            updateWrapper.set("unit_id", text.getUnitId());
        }
        if (text.getArticleType() != null) {
            updateWrapper.set("article_type", text.getArticleType().name());
            if (text.getArticleType() == com.english.entity.ArticleType.OUTER) {
                updateWrapper.set("book_volume", null);
                updateWrapper.set("content_section", null);
                updateWrapper.set("unit_num", null);
            }
        }
        if (text.getBookVolume() != null) {
            updateWrapper.set("book_volume", text.getBookVolume());
        }
        if (text.getContentSection() != null) {
            updateWrapper.set("content_section", text.getContentSection());
        }
        if (text.getUnitNum() != null) {
            updateWrapper.set("unit_num", text.getUnitNum());
        }
        if (text.getTitle() != null) {
            updateWrapper.set("title", text.getTitle());
        }
        updateWrapper.set("updated_time", LocalDateTime.now());

        // 如果有其他字段也需要更新
        if (text.getContent() != null) {
            updateWrapper.set("content", text.getContent());
        }
        if (text.getMarkdownContent() != null) {
            updateWrapper.set("markdown_content", text.getMarkdownContent());
        }
        if (text.getContentType() != null) {
            updateWrapper.set("content_type", text.getContentType());
        }
        if (text.getDifficulty() != null) {
            updateWrapper.set("difficulty", text.getDifficulty());
        }
        if (text.getAudioUrl() != null) {
            updateWrapper.set("audio_url", text.getAudioUrl());
        }
        if (text.getCoverUrl() != null) {
            updateWrapper.set("cover_url", text.getCoverUrl());
        }
        if (text.getTags() != null) {
            updateWrapper.set("tags", text.getTags());
        }
        if (text.getIsPublic() != null) {
            updateWrapper.set("is_public", text.getIsPublic());
        }
        if (text.getOrderNum() != null) {
            updateWrapper.set("order_num", text.getOrderNum());
        }
        if (text.getIsFeatured() != null) {
            updateWrapper.set("is_featured", text.getIsFeatured());
        }
        if (text.getFeaturedPriority() != null) {
            updateWrapper.set("featured_priority", text.getFeaturedPriority());
        }
        if (text.getStatus() != null) {
            updateWrapper.set("status", text.getStatus());
        }

        update(updateWrapper);
        if (text.getUnitId() != null && text.getUnitId() > 0) {
            textUnitMapper.insertRelation(text.getId(), text.getUnitId(), text.getOrderNum() == null ? 0 : text.getOrderNum());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "publicTexts", allEntries = true)
    public void deleteText(Long id) {
        // 先获取课文，检查是否存在
        Text text = getTextById(id);
        if (text == null) {
            throw new BusinessException("课文不存在或已被删除");
        }

        // 级联删除该课文的所有划线记录（数据清理）
        underlineService.deleteUnderlinesByTextId(id);

        // 级联删除该课文的所有习题（TextExercise 无 @TableLogic，物理删除）
        try {
            textExerciseMapper.deleteExercisesByTextId(id);
        } catch (Exception e) {
            log.warn("删除课文关联习题失败（可能text_exercises表未部署） textId=" + id + ": " + e.getMessage());
        }

        // 逻辑删除：@TableLogic 会将 removeById 转换为 UPDATE SET deleted=1
        // 注意：不能用 updateById + setDeleted(1)，因为 @TableLogic 会从 SET 子句中排除 deleted 字段
        removeById(id);
    }

    @Override
    public List<Text> getTextsByUnit(Long unitId) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(wrapper -> wrapper
                .eq("unit_id", unitId)
                .or()
                .inSql("id", "SELECT text_id FROM text_units WHERE unit_id = " + unitId));
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Text> getArticles(ArticleFilterDTO filter) {
        QueryWrapper<Text> wrapper = new QueryWrapper<>();
        if (filter != null && filter.getArticleType() != null) {
            wrapper.eq("article_type", filter.getArticleType().name());
        }
        if (filter != null && filter.getBookVolume() != null && !filter.getBookVolume().trim().isEmpty()) {
            wrapper.eq("book_volume", filter.getBookVolume().trim());
        }
        if (filter != null && filter.getContentSection() != null && !filter.getContentSection().trim().isEmpty()) {
            wrapper.eq("content_section", filter.getContentSection().trim());
        }
        if (filter != null && filter.getUnitNum() != null) {
            wrapper.eq("unit_num", filter.getUnitNum());
        }
        wrapper.eq("deleted", 0).eq("status", "APPROVED").orderByAsc("book_volume", "unit_num", "order_num");
        return list(wrapper);
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<Text> getArticlesPaged(ArticleFilterDTO filter) {
        int pageNum = (filter != null && filter.getPage() != null && filter.getPage() > 0) ? filter.getPage() : 1;
        int pageSize = (filter != null && filter.getSize() != null && filter.getSize() > 0) ? Math.min(filter.getSize(), 100) : 20;

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Text> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);

        QueryWrapper<Text> wrapper = new QueryWrapper<>();
        // 排除大文本字段，只返回轻量元数据
        wrapper.select("id", "title", "difficulty", "category",
                "tags", "article_type", "book_volume",
                "content_section", "unit_num", "order_num",
                "cover_url", "view_count", "read_count");

        if (filter != null && filter.getArticleType() != null) {
            wrapper.eq("article_type", filter.getArticleType().name());
        }
        if (filter != null && filter.getKeyword() != null && !filter.getKeyword().trim().isEmpty()) {
            wrapper.like("title", filter.getKeyword().trim());
        }
        wrapper.eq("deleted", 0).eq("status", "APPROVED").orderByAsc("book_volume", "unit_num", "order_num");
        return page(page, wrapper);
    }

    @Override
    public List<Text> getTextsByDifficulty(String difficulty) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("difficulty", difficulty);
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Text> searchTexts(String keyword) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", keyword)
                .or()
                .like("content", keyword)
            );
        }
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getTextStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Text> easyWrapper = new QueryWrapper<>();
        easyWrapper.eq("difficulty", "EASY");
        easyWrapper.eq("deleted", 0);
        Long easyCount = count(easyWrapper);

        QueryWrapper<Text> mediumWrapper = new QueryWrapper<>();
        mediumWrapper.eq("difficulty", "MEDIUM");
        mediumWrapper.eq("deleted", 0);
        Long mediumCount = count(mediumWrapper);

        QueryWrapper<Text> hardWrapper = new QueryWrapper<>();
        hardWrapper.eq("difficulty", "HARD");
        hardWrapper.eq("deleted", 0);
        Long hardCount = count(hardWrapper);

        stats.put("totalEasy", easyCount);
        stats.put("totalMedium", mediumCount);
        stats.put("totalHard", hardCount);
        stats.put("totalTexts", easyCount + mediumCount + hardCount);

        return stats;
    }

    // 新增用户课文相关方法实现
    // 读己写场景：用户"我的上传"页在管理员审核 / 用户上传后立即刷新，
    // 走从库会因主从复制延迟或复制中断而丢失最近几条记录，强制主库。
    @Override
    @UseMaster
    public List<Text> getTextsByUploader(Long uploaderId) {
        if (uploaderId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uploaded_by", uploaderId);
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Text> getPublicTexts(int page, int size, String difficulty, String keyword) {
        // 参数边界校验，防止负数导致 SQL 异常
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_public", 1);
        queryWrapper.eq("status", "APPROVED");
        queryWrapper.eq("deleted", 0);

        if (difficulty != null && !difficulty.trim().isEmpty()) {
            queryWrapper.eq("difficulty", difficulty);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("title", keyword)
                    .or()
                    .like("content", keyword)
                    .or()
                    .like("tags", keyword)
            );
        }


        queryWrapper.orderByDesc("created_time");

        // 使用 MyBatis-Plus Page 对象而非字符串拼接，防止 SQL 注入
        Page<Text> pageParam = new Page<>(page, size);
        IPage<Text> pageResult = page(pageParam, queryWrapper);

        return pageResult.getRecords();
    }

    @Override
    public Integer getMaxOrderInUnit(Long unitId) {
        try {
            QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unit_id", unitId);
            queryWrapper.select("MAX(order_num) as max_order");
            queryWrapper.eq("deleted", 0);

            Map<String, Object> result = getMap(queryWrapper);
            if (result == null || result.isEmpty()) {
                return null;
            }

            Number maxOrder = (Number) result.get("max_order");
            return maxOrder != null ? maxOrder.intValue() : null;
        } catch (Exception e) {
            return null; // 出现异常时返回null
        }
    }

    @Override
    public void incrementViewCount(Long textId) {
        UpdateWrapper<Text> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", textId)
               .eq("deleted", 0)
               .setSql("view_count = COALESCE(view_count, 0) + 1");
        update(wrapper);
    }

    @Override
    public void incrementLikeCount(Long textId) {
        UpdateWrapper<Text> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", textId)
               .eq("deleted", 0)
               .setSql("like_count = COALESCE(like_count, 0) + 1");
        update(wrapper);
    }
}
