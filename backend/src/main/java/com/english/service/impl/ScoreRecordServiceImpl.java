package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.ScoreRecord;
import com.english.mapper.ScoreRecordMapper;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.ScoreRecordService;
import com.english.service.StudentClassService;
import com.english.entity.Assignment;
import com.english.entity.Class;
import com.english.entity.User;
import com.english.mapper.UserMapper;
import com.english.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScoreRecordServiceImpl extends ServiceImpl<ScoreRecordMapper, ScoreRecord> implements ScoreRecordService {

    private static final Logger logger = LoggerFactory.getLogger(ScoreRecordServiceImpl.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    @Lazy
    private AssignmentService assignmentService;
    
    @Autowired
    private ClassService classService;
    
    @Autowired
    private StudentClassService studentClassService;
    
    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public void recordScore(ScoreRecord scoreRecord) {
        if (scoreRecord == null) {
            throw new BusinessException("分数记录不能为空");
        }

        // 验证学生ID
        if (scoreRecord.getStudentId() == null) {
            throw new BusinessException("学生ID不能为空");
        }

        // 验证分数有效性：分数必须在0-100之间
        Integer score = scoreRecord.getScore();
        if (score == null) {
            throw new BusinessException("分数不能为空");
        }
        if (score < 0 || score > 100) {
            throw new BusinessException("分数必须在0-100之间");
        }

        // 验证分数类型
        if (scoreRecord.getScoreType() == null || scoreRecord.getScoreType().trim().isEmpty()) {
            throw new BusinessException("分数类型不能为空");
        }

        if (scoreRecord.getCreatedTime() == null) {
            scoreRecord.setCreatedTime(LocalDateTime.now());
        }
        if (scoreRecord.getUpdatedTime() == null) {
            scoreRecord.setUpdatedTime(LocalDateTime.now());
        }

        try {
            save(scoreRecord);
        } catch (DuplicateKeyException e) {
            // 竞态条件：另一线程已先插入相同的分数记录
            logger.debug("分数记录已存在 (studentId={}, assignmentId={}), 改为更新模式",
                    scoreRecord.getStudentId(), scoreRecord.getAssignmentId());

            // 构建更新条件
            QueryWrapper<ScoreRecord> updateWrapper = new QueryWrapper<>();
            updateWrapper.eq("student_id", scoreRecord.getStudentId());
            if (scoreRecord.getAssignmentId() != null) {
                updateWrapper.eq("assignment_id", scoreRecord.getAssignmentId());
                updateWrapper.eq("score_type", scoreRecord.getScoreType());
            } else {
                updateWrapper.eq("course_id", scoreRecord.getCourseId());
                updateWrapper.eq("unit_id", scoreRecord.getUnitId());
                updateWrapper.eq("score_type", scoreRecord.getScoreType());
            }

            scoreRecord.setUpdatedTime(LocalDateTime.now());
            int updateCount = baseMapper.update(scoreRecord, updateWrapper);

            if (updateCount == 0) {
                // 若更新失败（记录被删除），再次尝试插入
                try {
                    save(scoreRecord);
                } catch (DuplicateKeyException retryException) {
                    // 仍然冲突，说明已被其他线程插入 - 最后再更新一次
                    logger.debug("分数记录再次冲突，最终更新一次 (studentId={}, assignmentId={})",
                            scoreRecord.getStudentId(), scoreRecord.getAssignmentId());
                    baseMapper.update(scoreRecord, updateWrapper);
                } catch (Exception retryException) {
                    logger.error("分数记录保存失败 (studentId={}, assignmentId={})",
                            scoreRecord.getStudentId(), scoreRecord.getAssignmentId(), retryException);
                    throw new BusinessException("保存分数记录失败，请稍后重试");
                }
            }
        }
    }

    @Override
    public List<Map<String, Object>> getStudentScores(Long studentId, String scoreType, Long courseId, Long unitId, Long assignmentId) {
        if (studentId == null) {
            return Collections.emptyList();
        }

        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);

        if (scoreType != null && !scoreType.trim().isEmpty()) {
            wrapper.eq("score_type", scoreType);
        }
        if (courseId != null) {
            wrapper.eq("course_id", courseId);
        }
        if (unitId != null) {
            wrapper.eq("unit_id", unitId);
        }
        if (assignmentId != null) {
            wrapper.eq("assignment_id", assignmentId);
        }

        wrapper.orderByDesc("created_time");

        List<ScoreRecord> records = list(wrapper);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (ScoreRecord record : records) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.getId());
            map.put("studentId", record.getStudentId());
            map.put("assignmentId", record.getAssignmentId());
            map.put("courseId", record.getCourseId());
            map.put("unitId", record.getUnitId());
            map.put("scoreType", record.getScoreType());
            map.put("score", record.getScore());
            map.put("fullScore", record.getFullScore());
            map.put("scoreDetails", record.getScoreDetails());
            map.put("remark", record.getRemark());
            map.put("createdTime", record.getCreatedTime());
            result.add(map);
        }

        return result;
    }

    @Override
    public Map<String, Object> getStudentAverageScore(Long studentId, String scoreType, Long courseId) {
        if (studentId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("averageScore", 0.0);
            result.put("weightedAverageScore", 0.0);
            result.put("totalCount", 0);
            result.put("totalScore", 0);
            return result;
        }

        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("student_id", studentId);

        if (scoreType != null && !scoreType.trim().isEmpty()) {
            wrapper.eq("score_type", scoreType);
        }
        if (courseId != null) {
            wrapper.eq("course_id", courseId);
        }

        List<ScoreRecord> records = list(wrapper);
        if (records == null || records.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("averageScore", 0.0);
            result.put("weightedAverageScore", 0.0);
            result.put("totalCount", 0);
            result.put("totalScore", 0);
            return result;
        }

        // 计算简单平均分
        long totalScore = records.stream().mapToLong(r -> r.getScore() != null ? r.getScore() : 0).sum();
        double averageScore = totalScore / (double) records.size();

        // 计算加权平均分（考虑任务权重）
        // 注意：所有有分数记录的任务都应该计入加权计算，包括分数为0的记录
        double weightedTotalScore = 0.0;
        double totalWeight = 0.0;
        for (ScoreRecord record : records) {
            if (record.getAssignmentId() != null) {
                // 获取任务的权重，使用 QueryWrapper 加 deleted=0 过滤
                QueryWrapper<Assignment> assignmentWrapper = new QueryWrapper<>();
                assignmentWrapper.eq("id", record.getAssignmentId());
                assignmentWrapper.eq("deleted", 0);
                Assignment assignment = assignmentService.getOne(assignmentWrapper);

                if (assignment == null) {
                    // 任务不存在或已删除，跳过该记录
                    logger.warn("计算平均分时：任务不存在 (assignmentId={})", record.getAssignmentId());
                    continue;
                }

                double weight = (assignment.getWeight() != null && assignment.getWeight() > 0)
                    ? assignment.getWeight() : 1.0;
                // 所有记录都计入，包括分数为0的（因为0分也是有效分数）
                int score = record.getScore() != null ? record.getScore() : 0;
                weightedTotalScore += score * weight;
                totalWeight += weight;
            } else {
                // 如果没有任务ID，使用简单平均
                int score = record.getScore() != null ? record.getScore() : 0;
                weightedTotalScore += score;
                totalWeight += 1.0;
            }
        }

        double weightedAverageScore = totalWeight > 0 ? weightedTotalScore / totalWeight : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("averageScore", Math.round(averageScore * 100.0) / 100.0);
        result.put("weightedAverageScore", Math.round(weightedAverageScore * 100.0) / 100.0);
        result.put("totalCount", records.size());
        // 注意：totalScore 仅用于统计，不应作为主要显示指标
        // 应该使用 weightedAverageScore 或 averageScore
        result.put("totalScore", (int) totalScore);

        // 添加日志以便调试
        logger.debug("计算学生平均分数: 学生ID={}, 记录数={}, 简单平均分={}, 加权平均分={}, 累加分数={}",
                studentId, records.size(), averageScore, weightedAverageScore, totalScore);

        return result;
    }

    @Override
    public Map<String, Object> getScoreStatistics(Long courseId, Long unitId, Long assignmentId) {
        // 防止无参数全表扫描 DoS
        if (courseId == null && unitId == null && assignmentId == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("averageScore", 0.0);
            result.put("maxScore", 0);
            result.put("minScore", 0);
            result.put("totalCount", 0);
            return result;
        }

        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();

        if (courseId != null) {
            wrapper.eq("course_id", courseId);
        }
        if (unitId != null) {
            wrapper.eq("unit_id", unitId);
        }
        if (assignmentId != null) {
            wrapper.eq("assignment_id", assignmentId);
        }

        List<ScoreRecord> records = list(wrapper);
        if (records == null || records.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("averageScore", 0.0);
            result.put("maxScore", 0);
            result.put("minScore", 0);
            result.put("totalCount", 0);
            return result;
        }

        int maxScore = records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).max().orElse(0);
        int minScore = records.stream().mapToInt(r -> r.getScore() != null ? r.getScore() : 0).min().orElse(0);
        long totalScoreSum = records.stream().mapToLong(r -> r.getScore() != null ? r.getScore() : 0).sum();
        double averageScore = totalScoreSum / (double) records.size();

        Map<String, Object> result = new HashMap<>();
        result.put("averageScore", Math.round(averageScore * 100.0) / 100.0);
        result.put("maxScore", maxScore);
        result.put("minScore", minScore);
        result.put("totalCount", records.size());
        return result;
    }

    @Override
    public ScoreRecord getAssignmentScore(Long assignmentId, Long studentId) {
        QueryWrapper<ScoreRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("assignment_id", assignmentId)
               .eq("student_id", studentId)
               .orderByDesc("created_time")
               .last("LIMIT 1");
        return getOne(wrapper);
    }

    @Override
    @Transactional
    public void updateAssignmentScore(Long assignmentId, Long studentId) {
        try {
            // 先获取任务信息（防止NPE）
            QueryWrapper<Assignment> assignmentWrapper = new QueryWrapper<>();
            assignmentWrapper.eq("id", assignmentId);
            assignmentWrapper.eq("deleted", 0);
            Assignment assignment = assignmentService.getOne(assignmentWrapper);

            if (assignment == null) {
                logger.warn("更新任务分数失败：任务不存在或已删除 (assignmentId={})", assignmentId);
                return;
            }

            // 获取任务进度
            Map<String, Object> progress = assignmentService.getAssignmentProgress(assignmentId, studentId);
            if (progress == null) {
                logger.warn("更新任务分数失败：无法获取任务进度 (assignmentId={}, studentId={})", assignmentId, studentId);
                return;
            }

            double completionRate = 0.0;
            Object completionRateObj = progress.get("completionRate");
            if (completionRateObj instanceof Number) {
                completionRate = ((Number) completionRateObj).doubleValue();
            } else if (completionRateObj != null) {
                try {
                    completionRate = Double.parseDouble(completionRateObj.toString());
                } catch (NumberFormatException e) {
                    logger.warn("无法解析完成率: {}", completionRateObj);
                }
            }

            // 获取学习时长（分钟）
            int studyTime = 0;
            Object studyTimeObj = progress.get("studyTime");
            if (studyTimeObj instanceof Number) {
                studyTime = ((Number) studyTimeObj).intValue();
            } else if (studyTimeObj != null) {
                try {
                    studyTime = Integer.parseInt(studyTimeObj.toString());
                } catch (NumberFormatException e) {
                    logger.warn("无法解析学习时长: {}", studyTimeObj);
                }
            }

            logger.debug("更新任务分数：assignmentId={}, studentId={}, completionRate={}, studyTime={}",
                             assignmentId, studentId, completionRate, studyTime);

            // 获取任务的理想学习时长（如果未设置，默认60分钟）
            int idealStudyTime = assignment.getIdealStudyTimeMinutes() != null
                ? assignment.getIdealStudyTimeMinutes() : 60;
            
            // 计算分数：综合考虑完成率和学习时长
            // 基础分数：完成率 * 60（最高60分）
            double baseScore = Math.min(completionRate * 60.0, 60.0);

            // 学习时长加分：根据学习时长给予额外分数（最高40分）
            // 使用教师设置的理想学习时长，超过理想时长按理想时长计算
            int normalizedStudyTime = Math.min(studyTime, idealStudyTime);
            double timeScore = (idealStudyTime > 0 ? (normalizedStudyTime / (double) idealStudyTime) * 40.0 : 0.0);
            timeScore = Math.min(timeScore, 40.0);

            // 总分数 = 基础分数 + 时长分数
            int score = (int) Math.round(baseScore + timeScore);
            score = Math.min(score, 100); // 确保不超过100分
            score = Math.max(score, 0);  // 确保不低于0分
            
            // 构建得分详情 JSON
            String scoreDetailsJson = null;
            try {
                Map<String, Object> details = new HashMap<>();
                details.put("completionRate", completionRate);
                details.put("studyTime", studyTime);
                details.put("baseScore", baseScore);
                details.put("timeScore", timeScore);
                details.put("progress", progress);
                scoreDetailsJson = OBJECT_MAPPER.writeValueAsString(details);
            } catch (Exception e) {
                // 忽略JSON序列化错误
            }

            // 查找是否已有分数记录
            ScoreRecord existingRecord = getAssignmentScore(assignmentId, studentId);

            if (existingRecord != null) {
                // 更新现有记录
                existingRecord.setScore(score);
                existingRecord.setFullScore(100);
                existingRecord.setUpdatedTime(LocalDateTime.now());
                existingRecord.setScoreDetails(scoreDetailsJson);
                updateById(existingRecord);
            } else {
                // 创建新记录
                ScoreRecord newRecord = new ScoreRecord();
                newRecord.setStudentId(studentId);
                newRecord.setAssignmentId(assignmentId);
                newRecord.setScoreType("ASSIGNMENT");
                newRecord.setScore(score);
                newRecord.setFullScore(100);
                newRecord.setCreatedTime(LocalDateTime.now());
                newRecord.setUpdatedTime(LocalDateTime.now());
                newRecord.setScoreDetails(scoreDetailsJson);

                // 获取任务信息以填充课程和单元ID
                try {
                    if (assignment.getUnitId() != null) {
                        newRecord.setUnitId(assignment.getUnitId());
                    }
                    if (assignment.getClassId() != null) {
                        Class classInfo = classService.getClassById(assignment.getClassId());
                        if (classInfo != null && classInfo.getCourseId() != null) {
                            newRecord.setCourseId(classInfo.getCourseId());
                        }
                    }
                } catch (Exception e) {
                    logger.debug("获取课程信息失败，继续保存分数记录 (assignmentId={}, studentId={})", assignmentId, studentId);
                }

                try {
                    save(newRecord);
                } catch (DuplicateKeyException e) {
                    // 竞态条件：另一线程已先插入，改为更新
                    // 使用 UPDATE 方式：先尝试更新，再尝试插入
                    logger.debug("分数记录并发插入冲突，改为更新模式 (assignmentId={}, studentId={})", assignmentId, studentId);

                    // 先尝试更新
                    QueryWrapper<ScoreRecord> updateWrapper = new QueryWrapper<>();
                    updateWrapper.eq("assignment_id", assignmentId);
                    updateWrapper.eq("student_id", studentId);

                    int updateCount = baseMapper.update(newRecord, updateWrapper);
                    if (updateCount == 0) {
                        // 若更新失败（另一线程已删除），再次尝试插入
                        try {
                            save(newRecord);
                        } catch (Exception retryException) {
                            logger.error("分数记录并发处理失败 (assignmentId={}, studentId={})", assignmentId, studentId, retryException);
                            throw new BusinessException("保存分数记录失败，请稍后重试");
                        }
                    }
                } catch (Exception e) {
                    logger.error("保存分数记录失败 (assignmentId={}, studentId={})", assignmentId, studentId, e);
                    throw new BusinessException("保存分数记录失败，请稍后重试");
                }
            }
        } catch (Exception e) {
            // 记录错误但不抛出异常，避免影响主流程
            logger.error("更新任务分数失败", e);
        }
    }

    @Override
    public List<Map<String, Object>> exportClassScores(Long classId) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (classId == null) {
            return result;
        }

        // 获取班级信息
        Class classInfo = classService.getClassById(classId);
        if (classInfo == null) {
            return result;
        }

        // 获取班级所有学生
        List<com.english.entity.StudentClass> studentClasses = studentClassService.getStudentsByClassId(classId);
        if (studentClasses == null || studentClasses.isEmpty()) {
            return result;
        }

        // 获取班级所有未删除的任务
        QueryWrapper<Assignment> assignmentWrapper = new QueryWrapper<>();
        assignmentWrapper.eq("class_id", classId);
        assignmentWrapper.eq("deleted", 0);
        List<Assignment> assignments = assignmentService.list(assignmentWrapper);
        if (assignments == null) {
            assignments = new ArrayList<>();
        }

        // 为每个学生生成成绩记录
        for (com.english.entity.StudentClass studentClass : studentClasses) {
            if (studentClass == null || studentClass.getStudentId() == null) {
                continue;
            }

            Long studentId = studentClass.getStudentId();
            User student = userMapper.selectById(studentId);
            if (student == null) {
                continue;
            }

            Map<String, Object> studentRecord = new HashMap<>();
            studentRecord.put("studentId", studentId);
            studentRecord.put("studentName", student.getRealName() != null ? student.getRealName() : student.getUsername());
            studentRecord.put("username", student.getUsername());

            // 获取该学生的所有任务成绩
            // 只记录有分数记录的任务
            List<Map<String, Object>> assignmentScores = new ArrayList<>();
            double totalWeightedScore = 0.0;  // 分数 × 权重的和
            double totalWeight = 0.0;  // 权重的和
            int scoredAssignmentsCount = 0;  // 有分数记录的任务数

            for (Assignment assignment : assignments) {
                if (assignment == null || assignment.getId() == null) {
                    continue;
                }

                ScoreRecord scoreRecord = getAssignmentScore(assignment.getId(), studentId);

                // 如果分数记录不存在，尝试自动计算并创建
                if (scoreRecord == null) {
                    try {
                        logger.debug("导出成绩：为任务 {} 的学生 {} 自动计算分数", assignment.getId(), studentId);
                        updateAssignmentScore(assignment.getId(), studentId);
                        scoreRecord = getAssignmentScore(assignment.getId(), studentId);
                        if (scoreRecord != null) {
                            logger.debug("导出成绩：成功计算分数，分数为 {}", scoreRecord.getScore());
                        } else {
                            logger.warn("导出成绩：警告 - 计算分数后仍无法获取记录");
                        }
                    } catch (Exception e) {
                        logger.error("导出成绩：自动计算任务分数失败 (assignmentId={}, studentId={})", assignment.getId(), studentId, e);
                    }
                } else {
                    logger.debug("导出成绩：找到现有分数记录，分数为 {}", scoreRecord.getScore());
                }

                // 只处理有分数记录的任务
                if (scoreRecord != null) {
                    Double weightObj = assignment.getWeight();
                    double weight = (weightObj != null && weightObj > 0) ? weightObj : 1.0;

                    // 确保分数不为null
                    int finalScore = scoreRecord.getScore() != null ? scoreRecord.getScore() : 0;
                    int finalFullScore = scoreRecord.getFullScore() != null ? scoreRecord.getFullScore() : 100;

                    // 添加到任务列表（只包含有分数记录的任务）
                    Map<String, Object> assignmentScore = new HashMap<>();
                    assignmentScore.put("assignmentId", assignment.getId());
                    assignmentScore.put("assignmentTitle", assignment.getTitle() != null ? assignment.getTitle() : "");
                    assignmentScore.put("weight", weight);
                    assignmentScore.put("score", finalScore);
                    assignmentScore.put("fullScore", finalFullScore);
                    assignmentScores.add(assignmentScore);

                    // 计算加权总分：分数 × 权重
                    double weightedContribution = finalScore * weight;
                    totalWeightedScore += weightedContribution;
                    totalWeight += weight;
                    scoredAssignmentsCount++;

                    logger.debug("导出成绩 - 任务计入: 任务ID={}, 标题={}, 分数={}, 权重={}, 加权贡献={}, 累计总分={}, 累计权重={}",
                            assignment.getId(), assignment.getTitle(), finalScore, weight, weightedContribution, totalWeightedScore, totalWeight);
                } else {
                    // 没有分数记录的任务，不计入加权平均分
                    // 因为学生还没有开始学习这个任务，不应该影响成绩
                    logger.debug("导出成绩 - 任务不计入: 任务ID={}, 标题={}, 原因=无分数记录（未开始）",
                            assignment.getId(), assignment.getTitle());
                }
            }

            studentRecord.put("assignments", assignmentScores);

            // 计算加权平均分
            // 公式：加权平均分 = Σ(分数 × 权重) / Σ(权重)
            // 只计算有分数记录的任务
            double weightedAverage = totalWeight > 0 ? totalWeightedScore / totalWeight : 0.0;
            double roundedAverage = Math.round(weightedAverage * 100.0) / 100.0;

            logger.debug("导出成绩计算详情: 学生ID={}, 学生姓名={}, 总任务数={}, 有分数记录的任务数={}, 加权总分={}, 总权重={}, 加权平均分={}",
                    studentId, studentRecord.get("studentName"), assignments.size(), scoredAssignmentsCount,
                    totalWeightedScore, totalWeight, roundedAverage);

            studentRecord.put("weightedAverage", roundedAverage);
            studentRecord.put("totalAssignments", assignments.size());
            studentRecord.put("completedAssignments", assignmentScores.stream()
                .mapToInt(a -> {
                    Object scoreObj = a.get("score");
                    if (scoreObj instanceof Number) {
                        return ((Number) scoreObj).intValue() > 0 ? 1 : 0;
                    }
                    return 0;
                })
                .sum());

            result.add(studentRecord);
        }

        return result;
    }

    @Override
    public byte[] generateClassScoresExcel(List<Map<String, Object>> data, Long classId) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("班级成绩");

            // 创建标题样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 创建数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            int rowNum = 0;

            // 创建表头
            Row headerRow = sheet.createRow(rowNum++);
            int colNum = 0;
            List<String> allHeaders = new ArrayList<>();
            allHeaders.add("学号");
            allHeaders.add("姓名");
            allHeaders.add("任务数");
            allHeaders.add("已完成");
            allHeaders.add("加权平均分");

            // 获取所有任务标题作为列头
            if (data != null && !data.isEmpty()) {
                Map<String, Object> firstStudent = data.get(0);
                if (firstStudent != null) {
                    Object assignmentsObj = firstStudent.get("assignments");
                    if (assignmentsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> assignments = (List<Map<String, Object>>) assignmentsObj;
                        if (assignments != null) {
                            for (Map<String, Object> assignment : assignments) {
                                if (assignment == null) continue;
                                Object titleObj = assignment.get("assignmentTitle");
                                String title = (titleObj instanceof String) ? (String) titleObj : "";
                                Object weightObj = assignment.get("weight");
                                Double weight = (weightObj instanceof Number) ? ((Number) weightObj).doubleValue() : 0.0;
                                allHeaders.add(title + " (权重:" + weight + ")");
                            }
                        }
                    }
                }
            }

            for (String header : allHeaders) {
                Cell cell = headerRow.createCell(colNum++);
                cell.setCellValue(header != null ? header : "");
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            if (data != null) {
                for (Map<String, Object> studentRecord : data) {
                    if (studentRecord == null) continue;

                    Row row = sheet.createRow(rowNum++);
                    colNum = 0;

                    // 学号
                    Cell cell0 = row.createCell(colNum++);
                    Object usernameObj = studentRecord.get("username");
                    cell0.setCellValue((usernameObj instanceof String) ? (String) usernameObj : "");
                    cell0.setCellStyle(dataStyle);

                    // 姓名
                    Cell cell1 = row.createCell(colNum++);
                    Object studentNameObj = studentRecord.get("studentName");
                    cell1.setCellValue((studentNameObj instanceof String) ? (String) studentNameObj : "");
                    cell1.setCellStyle(dataStyle);

                    // 任务数
                    Cell cell2 = row.createCell(colNum++);
                    Object totalAssignmentsObj = studentRecord.get("totalAssignments");
                    cell2.setCellValue(totalAssignmentsObj instanceof Number ? ((Number) totalAssignmentsObj).intValue() : 0);
                    cell2.setCellStyle(dataStyle);

                    // 已完成
                    Cell cell3 = row.createCell(colNum++);
                    Object completedAssignmentsObj = studentRecord.get("completedAssignments");
                    cell3.setCellValue(completedAssignmentsObj instanceof Number ? ((Number) completedAssignmentsObj).intValue() : 0);
                    cell3.setCellStyle(dataStyle);

                    // 加权平均分
                    Cell cell4 = row.createCell(colNum++);
                    Object weightedAverageObj = studentRecord.get("weightedAverage");
                    cell4.setCellValue(weightedAverageObj instanceof Number ? ((Number) weightedAverageObj).doubleValue() : 0.0);
                    cell4.setCellStyle(dataStyle);

                    // 各任务成绩
                    Object assignmentsObj = studentRecord.get("assignments");
                    if (assignmentsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> assignments = (List<Map<String, Object>>) assignmentsObj;
                        if (assignments != null) {
                            for (Map<String, Object> assignment : assignments) {
                                if (assignment == null) continue;
                                Cell cell = row.createCell(colNum++);
                                Object scoreObj = assignment.get("score");
                                Object fullScoreObj = assignment.get("fullScore");
                                int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0;
                                int fullScore = fullScoreObj instanceof Number ? ((Number) fullScoreObj).intValue() : 100;
                                cell.setCellValue(score + "/" + fullScore);
                                cell.setCellStyle(dataStyle);
                            }
                        }
                    }
                }
            }

            // 自动调整列宽
            for (int i = 0; i < allHeaders.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 1000, 15000));
            }

            // 写入到字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.error("生成Excel文件失败", e);
            throw new BusinessException("生成Excel文件失败");
        }
    }
}

