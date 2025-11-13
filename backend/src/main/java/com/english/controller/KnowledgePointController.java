package com.english.controller;

import com.english.dto.Result;
import com.english.entity.KnowledgePoint;
import com.english.service.KnowledgePointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge-points")
public class KnowledgePointController {

    @Autowired
    private KnowledgePointService knowledgePointService;

    /**
     * 获取所有知识点
     */
    @GetMapping
    public Result<List<KnowledgePoint>> getAllKnowledgePoints() {
        try {
            List<KnowledgePoint> knowledgePoints = knowledgePointService.getAllKnowledgePoints();
            return Result.success(knowledgePoints);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取树形结构知识点
     */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> getKnowledgePointTree() {
        try {
            List<KnowledgePoint> allPoints = knowledgePointService.getAllKnowledgePoints();

            // 构建树形结构
            List<Map<String, Object>> tree = buildTree(allPoints, null);
            return Result.success(tree);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按类别获取知识点
     */
    @GetMapping("/category/{category}")
    public Result<List<KnowledgePoint>> getKnowledgePointsByCategory(@PathVariable String category) {
        try {
            List<KnowledgePoint> knowledgePoints =
                knowledgePointService.getKnowledgePointsByCategory(category);
            return Result.success(knowledgePoints);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按考试类型获取知识点
     */
    @GetMapping("/exam-type/{examType}")
    public Result<List<KnowledgePoint>> getKnowledgePointsByExamType(@PathVariable String examType) {
        try {
            List<KnowledgePoint> knowledgePoints =
                knowledgePointService.getKnowledgePointsByExamType(examType);
            return Result.success(knowledgePoints);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 构建树形结构
     */
    private List<Map<String, Object>> buildTree(List<KnowledgePoint> points, Long parentId) {
        return points.stream()
            .filter(point -> {
                if (parentId == null) {
                    return point.getParentId() == null || point.getParentId() == 0;
                }
                return parentId.equals(point.getParentId());
            })
            .map(point -> {
                Map<String, Object> node = new HashMap<>();
                node.put("id", point.getId());
                node.put("name", point.getName());
                node.put("category", point.getCategory());
                node.put("examType", point.getExamType());
                node.put("description", point.getDescription());
                node.put("parentId", point.getParentId());

                // 递归获取子节点
                List<Map<String, Object>> children = buildTree(points, point.getId());
                if (!children.isEmpty()) {
                    node.put("children", children);
                }

                return node;
            })
            .collect(Collectors.toList());
    }
}
