package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.KnowledgePoint;
import com.english.mapper.KnowledgePointMapper;
import com.english.service.KnowledgePointService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint> implements KnowledgePointService {

    @Override
    public List<KnowledgePoint> getAllKnowledgePoints() {
        QueryWrapper<KnowledgePoint> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("category", "code");
        return list(queryWrapper);
    }

    @Override
    public List<KnowledgePoint> getKnowledgePointsByCategory(String category) {
        QueryWrapper<KnowledgePoint> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category", category);
        queryWrapper.orderByAsc("code");
        return list(queryWrapper);
    }

    @Override
    public List<KnowledgePoint> getKnowledgePointsByExamType(String examType) {
        QueryWrapper<KnowledgePoint> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("exam_type", examType);
        queryWrapper.orderByAsc("category", "code");
        return list(queryWrapper);
    }

    @Override
    public KnowledgePoint getKnowledgePointById(Long id) {
        return getById(id);
    }

    @Override
    public List<KnowledgePoint> getChildrenKnowledgePoints(Long parentId) {
        QueryWrapper<KnowledgePoint> queryWrapper = new QueryWrapper<>();
        if (parentId == null) {
            queryWrapper.isNull("parent_id").or().eq("parent_id", 0);
        } else {
            queryWrapper.eq("parent_id", parentId);
        }
        queryWrapper.orderByAsc("code");
        return list(queryWrapper);
    }
}
