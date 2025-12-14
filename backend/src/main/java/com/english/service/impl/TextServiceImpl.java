package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Text;
import com.english.mapper.TextMapper;
import com.english.service.TextService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TextServiceImpl extends ServiceImpl<TextMapper, Text> implements TextService {

    public Map<String, Object> getTextsByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        String keyword = (String) params.get("keyword");
        String difficulty = (String) params.get("difficulty");
        Long unitId = (Long) params.get("unitId");

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

        if (unitId != null) {
            queryWrapper.eq("unit_id", unitId);
        }

        queryWrapper.orderByDesc("created_time");

        IPage<Text> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    public Text getTextById(Long id) {
        return getById(id);
    }

    public void createText(Text text) {
        if (text.getDifficulty() == null) {
            text.setDifficulty("MEDIUM");
        }
        text.setCreatedTime(LocalDateTime.now());
        text.setUpdatedTime(LocalDateTime.now());
        text.setDeleted(0);
        save(text);
    }

    public void updateText(Text text) {
        text.setUpdatedTime(LocalDateTime.now());
        updateById(text);
    }

    public void deleteText(Long id) {
        // 先查询实体
        Text text = getById(id);
        if (text == null) {
            throw new RuntimeException("课文不存在");
        }
        // 使用 MyBatis Plus 的逻辑删除
        boolean success = removeById(id);
        if (!success) {
            throw new RuntimeException("删除失败");
        }
    }

    public List<Text> getTextsByUnit(Long unitId) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("unit_id", unitId);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    public List<Text> getTextsByDifficulty(String difficulty) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("difficulty", difficulty);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    public List<Text> searchTexts(String keyword) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", keyword)
                .or()
                .like("content", keyword)
            );
        }
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    public Map<String, Object> getTextStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Text> easyWrapper = new QueryWrapper<>();
        easyWrapper.eq("difficulty", "EASY");
        Long easyCount = count(easyWrapper);

        QueryWrapper<Text> mediumWrapper = new QueryWrapper<>();
        mediumWrapper.eq("difficulty", "MEDIUM");
        Long mediumCount = count(mediumWrapper);

        QueryWrapper<Text> hardWrapper = new QueryWrapper<>();
        hardWrapper.eq("difficulty", "HARD");
        Long hardCount = count(hardWrapper);

        stats.put("totalEasy", easyCount);
        stats.put("totalMedium", mediumCount);
        stats.put("totalHard", hardCount);
        stats.put("totalTexts", easyCount + mediumCount + hardCount);

        return stats;
    }

    @Override
    public List<Text> getTextsByCourse(Long courseId) {
        QueryWrapper<Text> queryWrapper = new QueryWrapper<>();
        queryWrapper.inSql("unit_id", "SELECT id FROM units WHERE course_id = " + courseId);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public int importTextFromFile(Long unitId, Long courseId, MultipartFile file, String contentType, Integer orderNum, String customTitle) throws Exception {
        if (file.isEmpty()) {
            throw new Exception("文件为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new Exception("文件名为空");
        }

        String content;
        String title;

        // 根据文件类型解析内容
        if (filename.endsWith(".txt")) {
            // 解析 TXT 文件
            try (InputStream is = file.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                content = new String(bytes, "UTF-8");
            }
            title = filename.substring(0, filename.lastIndexOf("."));
        } else if (filename.endsWith(".docx") || filename.endsWith(".doc")) {
            // 解析 Word 文件
            try (InputStream is = file.getInputStream();
                 XWPFDocument document = new XWPFDocument(is)) {

                StringBuilder contentBuilder = new StringBuilder();
                List<XWPFParagraph> paragraphs = document.getParagraphs();

                for (XWPFParagraph paragraph : paragraphs) {
                    String text = paragraph.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        contentBuilder.append(text).append("\n");
                    }
                }

                content = contentBuilder.toString().trim();
                title = filename.substring(0, filename.lastIndexOf("."));
            }
        } else {
            throw new Exception("不支持的文件格式，仅支持 .txt, .doc, .docx");
        }

        if (content == null || content.trim().isEmpty()) {
            throw new Exception("文档内容为空");
        }

        // 如果有自定义标题，使用自定义标题
        if (customTitle != null && !customTitle.trim().isEmpty()) {
            title = customTitle.trim();
        }

        // 创建课文对象
        Text text = new Text();
        text.setUnitId(unitId);
        text.setTitle(title);
        text.setContent(content);
        text.setContentType(contentType != null ? contentType : "ARTICLE");
        text.setOrderNum(orderNum != null ? orderNum : 1);
        text.setDifficulty("MEDIUM");
        text.setDeleted(0);
        text.setCreatedTime(LocalDateTime.now());
        text.setUpdatedTime(LocalDateTime.now());

        save(text);
        return 1;
    }
}
