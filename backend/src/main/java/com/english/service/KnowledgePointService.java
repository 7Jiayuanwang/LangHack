package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.KnowledgePoint;

import java.util.List;

public interface KnowledgePointService extends IService<KnowledgePoint> {
    List<KnowledgePoint> getAllKnowledgePoints();
    List<KnowledgePoint> getKnowledgePointsByCategory(String category);
    List<KnowledgePoint> getKnowledgePointsByExamType(String examType);
    KnowledgePoint getKnowledgePointById(Long id);
    List<KnowledgePoint> getChildrenKnowledgePoints(Long parentId);
}
