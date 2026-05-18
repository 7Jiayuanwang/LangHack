package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Text;
import com.english.entity.Underline;
import com.english.exception.BusinessException;
import com.english.mapper.UnderlineMapper;
import com.english.service.TextService;
import com.english.service.UnderlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnderlineServiceImpl implements UnderlineService {

    private static final Logger log = LoggerFactory.getLogger(UnderlineServiceImpl.class);

    @Autowired
    private UnderlineMapper underlineMapper;

    @Autowired
    @Lazy
    private TextService textService;

    @Override
    @Transactional
    public void createUnderline(Long userId, Long textId, String selectedText, Integer startIndex, Integer endIndex,
            String serializedRange) {
        // 参数非空校验
        if (userId == null || textId == null) {
            throw new BusinessException("用户ID和课文ID不能为空");
        }

        // 验证选中文本不为空
        if (selectedText == null || selectedText.trim().isEmpty()) {
            throw new BusinessException("选中文本不能为空");
        }

        // 验证索引范围合法性
        if (startIndex == null || endIndex == null) {
            throw new BusinessException("开始和结束位置不能为空");
        }
        if (startIndex < 0 || endIndex < 0 || startIndex >= endIndex) {
            throw new BusinessException("选中范围不合法");
        }

        // 校验文本存在且未被删除
        Text text = textService.getTextById(textId);
        if (text == null) {
            throw new BusinessException("课文不存在或已删除");
        }

        // 验证文本内容非空（Markdown 文章内容在 markdownContent 字段）
        // 索引值由前端基于渲染后的 DOM 计算，不做超出校验
        boolean textContent = text.getContent() != null && !text.getContent().trim().isEmpty();
        boolean mdContent = text.getMarkdownContent() != null && !text.getMarkdownContent().trim().isEmpty();
        if (!textContent && !mdContent) {
            throw new BusinessException("课文内容为空，无法划线");
        }

        Underline underline = new Underline();
        underline.setUserId(userId);
        underline.setTextId(textId);
        underline.setSelectedText(selectedText);
        underline.setStartIndex(startIndex);
        underline.setEndIndex(endIndex);
        underline.setSerializedRange(serializedRange);
        underlineMapper.insert(underline);
    }

    @Override
    public List<Underline> getUnderlinesByTextId(Long textId) {
        if (textId == null) {
            throw new BusinessException("课文ID不能为空");
        }
        QueryWrapper<Underline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("text_id", textId);
        return underlineMapper.selectList(queryWrapper);
    }

    @Override
    public List<Underline> getUnderlinesByTextIdAndUserId(Long textId, Long userId) {
        if (textId == null || userId == null) {
            throw new BusinessException("课文ID和用户ID不能为空");
        }
        QueryWrapper<Underline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("text_id", textId).eq("user_id", userId);
        return underlineMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public void deleteUnderlinesByTextId(Long textId) {
        if (textId == null) {
            throw new BusinessException("课文ID不能为空");
        }
        QueryWrapper<Underline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("text_id", textId);
        int deleted = underlineMapper.delete(queryWrapper);
        if (deleted > 0) {
            log.info("级联删除 textId={} 的所有划线记录，共 {} 条", textId, deleted);
        }
    }

    @Override
    public Underline getUnderlineById(Long underlineId) {
        if (underlineId == null) {
            return null;
        }
        return underlineMapper.selectById(underlineId);
    }

    @Override
    @Transactional
    public boolean deleteUnderline(Long underlineId, Long userId) {
        if (underlineId == null || userId == null) {
            throw new BusinessException("划线ID和用户ID不能为空");
        }

        // IDOR防护：验证该划线属于该用户
        QueryWrapper<Underline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", underlineId).eq("user_id", userId);
        Underline underline = underlineMapper.selectOne(queryWrapper);

        if (underline == null) {
            throw new BusinessException("划线不存在或无权限删除");
        }

        // 删除
        int deleted = underlineMapper.delete(queryWrapper);
        return deleted > 0;
    }
}
