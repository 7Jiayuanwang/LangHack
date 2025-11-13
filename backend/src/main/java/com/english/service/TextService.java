package com.english.service;

import com.english.entity.Text;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface TextService {
    Map<String, Object> getTextsByPage(Map<String, Object> params);
    Text getTextById(Long id);
    void createText(Text text);
    void updateText(Text text);
    void deleteText(Long id);
    List<Text> getTextsByUnit(Long unitId);
    List<Text> getTextsByCourse(Long courseId);
    List<Text> getTextsByDifficulty(String difficulty);
    List<Text> searchTexts(String keyword);
    Map<String, Object> getTextStats();

    // 文档导入（支持 txt, doc, docx）
    int importTextFromFile(Long unitId, Long courseId, MultipartFile file, String contentType, Integer orderNum, String customTitle) throws Exception;
}