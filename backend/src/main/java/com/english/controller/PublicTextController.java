package com.english.controller;

import com.english.dto.Result;
import com.english.dto.TextVersionsDTO;
import com.english.entity.Text;
import com.english.service.ArticleVersionService;
import com.english.service.TextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;

/**
 * 公开课文控制器
 * 为普通用户提供随机课文访问
 */
@RestController
@RequestMapping("/api/public/texts")
public class PublicTextController {

    private static final Logger log = LoggerFactory.getLogger(PublicTextController.class);

    @Autowired
    private TextService textService;

    @Autowired
    private ArticleVersionService articleVersionService;

    /**
     * 获取随机课文列表
     */
    @GetMapping("/random")
    public Result<List<Text>> getRandomTexts(
            @RequestParam(defaultValue = "8") int count,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String includeUserContent) {
        try {
            // 限制最大获取数量
            count = Math.min(Math.max(count, 1), 50);

            List<Text> allTexts;

            // 根据是否包含用户内容来决定查询策略
            if ("false".equals(includeUserContent)) {
                // 只获取系统内容，不需要公开和审核状态限制
                allTexts = textService.lambdaQuery()
                        .eq(Text::getDeleted, 0)
                        .isNull(Text::getUploadedBy) // 只获取系统内容
                        .orderByDesc(Text::getCreatedAt)
                        .list();
            } else {
                // 获取所有课文（系统内容 + 公开的用户内容）
                List<Text> systemTexts = textService.lambdaQuery()
                        .eq(Text::getDeleted, 0)
                        .isNull(Text::getUploadedBy) // 系统内容
                        .orderByDesc(Text::getCreatedAt)
                        .list();

                List<Text> userPublicTexts = textService.lambdaQuery()
                        .eq(Text::getDeleted, 0)
                        .isNotNull(Text::getUploadedBy) // 用户内容
                        .eq(Text::getIsPublic, 1) // 公开
                        .eq(Text::getStatus, "APPROVED") // 审核通过
                        .orderByDesc(Text::getCreatedAt)
                        .list();

                // 合并系统内容和公开用户内容
                allTexts = new java.util.ArrayList<>();
                allTexts.addAll(systemTexts);
                allTexts.addAll(userPublicTexts);
            }

            // 根据难度过滤
            if (difficulty != null && !difficulty.trim().isEmpty()) {
                allTexts = allTexts.stream()
                        .filter(text -> difficulty.equals(text.getDifficulty()))
                        .toList();
            }

            // 随机选取指定数量的课文
            List<Text> randomTexts;
            if (allTexts.size() <= count) {
                randomTexts = allTexts;
            } else {
                List<Text> shuffled = new ArrayList<>(allTexts);
                Collections.shuffle(shuffled);
                randomTexts = shuffled.subList(0, count);
            }

            return Result.success(randomTexts);
        } catch (Exception e) {
            log.error("获取随机课文失败", e);
            return Result.error("获取随机课文失败，请稍后重试");
        }
    }

    /**
     * 获取课文统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getTextStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 统计系统课文数量
            long systemTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .isNull(Text::getUploadedBy)
                    .count();

            // 统计用户上传课文数量
            long userTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .isNotNull(Text::getUploadedBy)
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            // 统计总公开内容数量
            long totalPublicTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            // 按难度统计
            long easyTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "EASY")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            long mediumTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "MEDIUM")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            long hardTexts = textService.lambdaQuery()
                    .eq(Text::getDeleted, 0)
                    .eq(Text::getDifficulty, "HARD")
                    .eq(Text::getIsPublic, 1)
                    .eq(Text::getStatus, "APPROVED")
                    .count();

            stats.put("totalSystemTexts", systemTexts);
            stats.put("totalUserTexts", userTexts);
            stats.put("totalPublicTexts", totalPublicTexts);
            stats.put("totalEasyTexts", easyTexts);
            stats.put("totalMediumTexts", mediumTexts);
            stats.put("totalHardTexts", hardTexts);
            stats.put("totalTexts", totalPublicTexts);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return Result.error("获取统计数据失败，请稍后重试");
        }
    }

    /**
     * 获取课文详情（包含所有版本）
     */
    @GetMapping("/{textId}")
    public Result<TextVersionsDTO> getTextDetail(@PathVariable Long textId) {
        try {
            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            // 验证课文是否可访问：系统内容 或 公开+已审核的用户内容
            boolean isAccessible = false;
            if (text.getUploadedBy() == null) {
                // 系统内容，总是可访问
                isAccessible = true;
            } else {
                // 用户上传的内容，需要公开且审核通过
                if ((text.getIsPublic() != null && text.getIsPublic() == 1) && "APPROVED".equals(text.getStatus())) {
                    isAccessible = true;
                }
            }

            if (!isAccessible) {
                return Result.error("课文不存在或未公开");
            }

            // 原子递增查看次数
            textService.incrementViewCount(textId);

            // 触发异步版本生成（如果缺失）
            articleVersionService.checkAndGenerateVersions(textId);

            // 刷新查看次数（原子操作已执行）
            text = textService.getTextById(textId);

            // 构建版本DTO
            TextVersionsDTO dto = new TextVersionsDTO();
            dto.setId(text.getId());
            dto.setTitle(text.getTitle());
            dto.setDifficulty(text.getDifficulty());
            dto.setAudioUrl(text.getAudioUrl());
            dto.setViewCount(text.getViewCount() != null ? text.getViewCount() : 0);
            dto.setLikeCount(text.getLikeCount());
            dto.setContentType(text.getContentType());
            dto.setMarkdownContent(text.getMarkdownContent());

            // 根据内容类型返回相应版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // Markdown内容，返回Markdown版本
                if (text.getMarkdownContent() != null && !text.getMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("original", text.getMarkdownContent());
                }
                if (text.getCet4MarkdownContent() != null && !text.getCet4MarkdownContent().trim().isEmpty()) {
                    dto.addVersion("cet4", text.getCet4MarkdownContent());
                }
                if (text.getCet6MarkdownContent() != null && !text.getCet6MarkdownContent().trim().isEmpty()) {
                    dto.addVersion("cet6", text.getCet6MarkdownContent());
                }
                if (text.getAcademicMarkdownContent() != null && !text.getAcademicMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("academic", text.getAcademicMarkdownContent());
                }
                if (text.getChineseParallelMarkdownContent() != null && !text.getChineseParallelMarkdownContent().trim().isEmpty()) {
                    dto.addVersion("chinese_parallel", text.getChineseParallelMarkdownContent());
                }
            } else {
                // 纯文本内容
                dto.addVersion("original", text.getContent());
                dto.addVersion("cet4", text.getCet4Content());
                dto.addVersion("cet6", text.getCet6Content());
                dto.addVersion("academic", text.getAcademicContent());
                dto.addVersion("chinese_parallel", text.getChineseParallelContent());
            }

            return Result.success(dto);
        } catch (Exception e) {
            log.error("获取课文详情失败, textId={}", textId, e);
            return Result.error("获取课文详情失败，请稍后重试");
        }
    }
}