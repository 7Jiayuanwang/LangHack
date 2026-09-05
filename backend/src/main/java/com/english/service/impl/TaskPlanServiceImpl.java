package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.dto.Result;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.TaskPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskPlanServiceImpl implements TaskPlanService {

    private static final Logger logger = LoggerFactory.getLogger(TaskPlanServiceImpl.class);

    @Autowired private TaskPlanMapper taskPlanMapper;
    @Autowired private TaskPlanClassMapper taskPlanClassMapper;
    @Autowired private TaskPlanRecordMapper taskPlanRecordMapper;
    @Autowired private ClassMapper classMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private LearningRecordMapper learningRecordMapper;
    @Autowired private StudentClassMapper studentClassMapper;
    @Autowired private TeachingClassMapper teachingClassMapper;

    // ── createPlan ──
    @Override
    @Transactional
    public Result<?> createPlan(Map<String, Object> params, Long creatorId) {
        TaskPlan plan = new TaskPlan();
        plan.setTitle((String) params.get("title"));
        plan.setCourseId(toLong(params.get("courseId")));
        plan.setCycleType((String) params.get("cycleType"));
        plan.setSemesterStart(LocalDate.parse((String) params.get("semesterStart")));
        plan.setSemesterEnd(LocalDate.parse((String) params.get("semesterEnd")));
        plan.setArticleTarget(toInt(params.get("articleTarget"), 3));
        plan.setWordTarget(toInt(params.get("wordTarget"), 50));
        plan.setWordCategory((String) params.getOrDefault("wordCategory", "CET4"));
        plan.setPaperTarget(toInt(params.get("paperTarget"), 1));
        plan.setArticleEnabled(toInt(params.get("articleEnabled"), 1));
        plan.setWordEnabled(toInt(params.get("wordEnabled"), 1));
        plan.setPaperEnabled(toInt(params.get("paperEnabled"), 1));
        plan.setArticleWeight(toInt(params.get("articleWeight"), 40));
        plan.setWordWeight(toInt(params.get("wordWeight"), 30));
        plan.setPaperWeight(toInt(params.get("paperWeight"), 30));
        int wSum = plan.getArticleWeight() + plan.getWordWeight() + plan.getPaperWeight();
        if (wSum != 100) return Result.error("权重之和必须为100，当前为" + wSum);
        plan.setTotalScore(params.get("totalScore") != null ? new BigDecimal(params.get("totalScore").toString()) : new BigDecimal("100"));
        plan.setStatus("DRAFT");
        plan.setCreatedBy(creatorId);
        taskPlanMapper.insert(plan);

        List<?> classIds = (List<?>) params.get("classIds");
        if (classIds != null) {
            for (Object cid : classIds) {
                TaskPlanClass tpc = new TaskPlanClass();
                tpc.setPlanId(plan.getId());
                tpc.setClassId(toLong(cid));
                tpc.setOptedOut(0);
                taskPlanClassMapper.insert(tpc);
            }
        }
        return Result.success(plan.getId());
    }

    // ── updatePlan ──
    @Override
    @Transactional
    public Result<?> updatePlan(Long planId, Map<String, Object> params) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        if (!"DRAFT".equals(plan.getStatus())) return Result.error("只能编辑草稿状态的计划");

        if (params.containsKey("title")) plan.setTitle((String) params.get("title"));
        if (params.containsKey("courseId")) plan.setCourseId(toLong(params.get("courseId")));
        if (params.containsKey("cycleType")) plan.setCycleType((String) params.get("cycleType"));
        if (params.containsKey("semesterStart")) plan.setSemesterStart(LocalDate.parse((String) params.get("semesterStart")));
        if (params.containsKey("semesterEnd")) plan.setSemesterEnd(LocalDate.parse((String) params.get("semesterEnd")));
        if (params.containsKey("articleTarget")) plan.setArticleTarget(toInt(params.get("articleTarget"), 3));
        if (params.containsKey("wordTarget")) plan.setWordTarget(toInt(params.get("wordTarget"), 50));
        if (params.containsKey("wordCategory")) plan.setWordCategory((String) params.get("wordCategory"));
        if (params.containsKey("paperTarget")) plan.setPaperTarget(toInt(params.get("paperTarget"), 1));
        if (params.containsKey("articleEnabled")) plan.setArticleEnabled(toInt(params.get("articleEnabled"), 1));
        if (params.containsKey("wordEnabled")) plan.setWordEnabled(toInt(params.get("wordEnabled"), 1));
        if (params.containsKey("paperEnabled")) plan.setPaperEnabled(toInt(params.get("paperEnabled"), 1));
        if (params.containsKey("articleWeight")) plan.setArticleWeight(toInt(params.get("articleWeight"), 40));
        if (params.containsKey("wordWeight")) plan.setWordWeight(toInt(params.get("wordWeight"), 30));
        if (params.containsKey("paperWeight")) plan.setPaperWeight(toInt(params.get("paperWeight"), 30));
        if (params.containsKey("totalScore")) plan.setTotalScore(new BigDecimal(params.get("totalScore").toString()));
        taskPlanMapper.updateById(plan);

        if (params.containsKey("classIds")) {
            taskPlanClassMapper.delete(new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, planId));
            List<?> classIds = (List<?>) params.get("classIds");
            if (classIds != null) {
                for (Object cid : classIds) {
                    TaskPlanClass tpc = new TaskPlanClass();
                    tpc.setPlanId(planId);
                    tpc.setClassId(toLong(cid));
                    tpc.setOptedOut(0);
                    taskPlanClassMapper.insert(tpc);
                }
            }
        }
        return Result.success("更新成功");
    }

    // ── publishPlan / closePlan ──
    @Override
    public Result<?> publishPlan(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        if (!"DRAFT".equals(plan.getStatus())) return Result.error("只能发布草稿状态的计划");
        plan.setStatus("ACTIVE");
        taskPlanMapper.updateById(plan);
        return Result.success("已发布");
    }

    @Override
    public Result<?> closePlan(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        plan.setStatus("CLOSED");
        taskPlanMapper.updateById(plan);
        return Result.success("已关闭");
    }

    // ── listPlans ──
    @Override
    public Result<?> listPlans(Map<String, Object> params) {
        LambdaQueryWrapper<TaskPlan> qw = new LambdaQueryWrapper<>();
        if (params != null && params.get("status") != null) {
            qw.eq(TaskPlan::getStatus, params.get("status"));
        }
        qw.orderByDesc(TaskPlan::getCreatedTime);
        List<TaskPlan> list = taskPlanMapper.selectList(qw);
        for (TaskPlan p : list) enrichPlan(p);
        return Result.success(list);
    }

    // ── getPlanDetail ──
    @Override
    public Result<?> getPlanDetail(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        enrichPlan(plan);
        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, planId));
        for (TaskPlanClass tpc : classes) enrichPlanClass(tpc);
        Map<String, Object> detail = new HashMap<>();
        detail.put("plan", plan);
        detail.put("classes", classes);
        return Result.success(detail);
    }

    // ── getPlanStats ──
    @Override
    public Result<?> getPlanStats(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, planId).eq(TaskPlanClass::getOptedOut, 0));

        LocalDate today = LocalDate.now();
        int totalStudents = 0;
        long sumArticles = 0, sumWords = 0, sumPapers = 0;
        List<Map<String, Object>> classDetails = new ArrayList<>();

        for (TaskPlanClass tpc : classes) {
            enrichPlanClass(tpc);
            int at = resolveInt(tpc.getArticleTargetOverride(), plan.getArticleTarget());
            int wt = resolveInt(tpc.getWordTargetOverride(), plan.getWordTarget());
            int pt = resolveInt(tpc.getPaperTargetOverride(), plan.getPaperTarget());
            String effCycle = (tpc.getCycleTypeOverride() != null) ? tpc.getCycleTypeOverride() : plan.getCycleType();
            String wordCat = (tpc.getWordCategoryOverride() != null) ? tpc.getWordCategoryOverride() : plan.getWordCategory();
            boolean artEnabled = resolveEnabled(plan.getArticleEnabled(), tpc.getArticleEnabledOverride());
            boolean wrdEnabled = resolveEnabled(plan.getWordEnabled(), tpc.getWordEnabledOverride());

            LocalDate periodStart, periodEnd;
            if ("WEEKLY".equals(effCycle)) {
                periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                periodEnd = periodStart.plusDays(6);
            } else {
                periodStart = today.withDayOfMonth(1);
                periodEnd = today.with(TemporalAdjusters.lastDayOfMonth());
            }
            LocalDateTime since = periodStart.atStartOfDay();
            LocalDateTime until = periodEnd.plusDays(1).atStartOfDay();
            String cats = wrdEnabled ? buildCategoryIn(wordCat) : null;

            List<StudentClass> scList = studentClassMapper.selectList(
                new LambdaQueryWrapper<StudentClass>().eq(StudentClass::getClassId, tpc.getClassId()));
            int sc = scList.size();
            totalStudents += sc;
            int ca = 0, cw = 0, cp = 0;
            for (StudentClass s : scList) {
                Long sid = s.getStudentId();
                ca += artEnabled ? learningRecordMapper.countDistinctTextsRead(sid, since, until) : 0;
                cw += (wrdEnabled && cats != null) ? learningRecordMapper.countDistinctWordsReviewedByCategory(sid, since, until, cats) : 0;
            }
            sumArticles += ca; sumWords += cw; sumPapers += cp;

            Map<String, Object> cd = new HashMap<>();
            cd.put("className", tpc.getClassName());
            cd.put("studentCount", sc);
            cd.put("avgArticleRate", sc > 0 && at > 0 ? Math.round((double) ca / sc / at * 100) : 0);
            cd.put("avgWordRate", sc > 0 && wt > 0 ? Math.round((double) cw / sc / wt * 100) : 0);
            cd.put("avgPaperRate", sc > 0 && pt > 0 ? Math.round((double) cp / sc / pt * 100) : 0);
            classDetails.add(cd);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("avgArticles", totalStudents > 0 ? Math.round((double) sumArticles / totalStudents) : 0);
        result.put("avgWords", totalStudents > 0 ? Math.round((double) sumWords / totalStudents) : 0);
        result.put("avgPapers", totalStudents > 0 ? Math.round((double) sumPapers / totalStudents) : 0);
        result.put("classDetails", classDetails);
        return Result.success(result);
    }

    // ── exportPlanScore ──
    @Override
    public byte[] exportPlanScore(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return new byte[0];
        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>()
                .eq(TaskPlanClass::getPlanId, planId).eq(TaskPlanClass::getOptedOut, 0));
        LocalDate today = LocalDate.now();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("成绩");
            CellStyle headerStyle = createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {"学号", "姓名", "班级", "文章完成数", "单词完成数", "真题完成数", "平时分"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (TaskPlanClass tpc : classes) {
                enrichPlanClass(tpc);
                int[] eff = resolveEffective(plan, tpc);
                LocalDateTime[] period = resolvePeriod(plan, tpc, today);
                String cats = resolveEnabled(plan.getWordEnabled(), tpc.getWordEnabledOverride()) ? buildCategoryIn(resolveWordCat(plan, tpc)) : null;
                boolean artEn = resolveEnabled(plan.getArticleEnabled(), tpc.getArticleEnabledOverride());
                boolean wrdEn = resolveEnabled(plan.getWordEnabled(), tpc.getWordEnabledOverride());
                List<StudentClass> scList = studentClassMapper.selectList(new LambdaQueryWrapper<StudentClass>().eq(StudentClass::getClassId, tpc.getClassId()));
                for (StudentClass sc : scList) {
                    Long sid = sc.getStudentId();
                    User u = userMapper.selectById(sid);
                    String name = (u != null && u.getRealName() != null) ? u.getRealName() : (u != null ? u.getUsername() : "");
                    String studentNo = (u != null && u.getUsername() != null) ? u.getUsername() : String.valueOf(sid);
                    int articles = artEn ? learningRecordMapper.countDistinctTextsRead(sid, period[0], period[1]) : 0;
                    int words = (wrdEn && cats != null) ? learningRecordMapper.countDistinctWordsReviewedByCategory(sid, period[0], period[1], cats) : 0;
                    BigDecimal score = calculateScore(plan.getTotalScore(), eff[0], eff[1], eff[2], eff[3], eff[4], eff[5], articles, words, 0);
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(studentNo);
                    row.createCell(1).setCellValue(name);
                    row.createCell(2).setCellValue(tpc.getClassName() != null ? tpc.getClassName() : "");
                    row.createCell(3).setCellValue(articles);
                    row.createCell(4).setCellValue(words);
                    row.createCell(5).setCellValue(0);
                    row.createCell(6).setCellValue(score.doubleValue());
                }
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("导出Excel失败", e);
            return new byte[0];
        }
    }

    // ── listTeacherPlans ──
    @Override
    public Result<?> listTeacherPlans(Long teacherId) {
        List<com.english.entity.Class> teacherClasses = classMapper.selectList(
            new LambdaQueryWrapper<com.english.entity.Class>().eq(com.english.entity.Class::getTeacherId, teacherId));
        if (teacherClasses.isEmpty()) return Result.success(Collections.emptyList());
        Set<Long> classIdSet = teacherClasses.stream().map(com.english.entity.Class::getId).collect(Collectors.toSet());
        List<TaskPlanClass> tpcList = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().in(TaskPlanClass::getClassId, classIdSet));
        if (tpcList.isEmpty()) return Result.success(Collections.emptyList());
        Set<Long> planIds = tpcList.stream().map(TaskPlanClass::getPlanId).collect(Collectors.toSet());
        List<TaskPlan> plans = taskPlanMapper.selectList(
            new LambdaQueryWrapper<TaskPlan>().in(TaskPlan::getId, planIds).eq(TaskPlan::getStatus, "ACTIVE"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskPlan p : plans) {
            enrichPlan(p);
            List<TaskPlanClass> myClasses = tpcList.stream()
                .filter(c -> c.getPlanId().equals(p.getId()) && classIdSet.contains(c.getClassId()))
                .collect(Collectors.toList());
            myClasses.forEach(this::enrichPlanClass);
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId()); item.put("title", p.getTitle());
            item.put("courseName", p.getCourseName()); item.put("cycleType", p.getCycleType());
            item.put("semesterStart", p.getSemesterStart()); item.put("semesterEnd", p.getSemesterEnd());
            item.put("articleTarget", p.getArticleTarget()); item.put("wordTarget", p.getWordTarget());
            item.put("wordCategory", p.getWordCategory()); item.put("paperTarget", p.getPaperTarget());
            item.put("articleWeight", p.getArticleWeight()); item.put("wordWeight", p.getWordWeight());
            item.put("paperWeight", p.getPaperWeight()); item.put("totalScore", p.getTotalScore());
            item.put("articleEnabled", p.getArticleEnabled()); item.put("wordEnabled", p.getWordEnabled());
            item.put("paperEnabled", p.getPaperEnabled()); item.put("status", p.getStatus());
            item.put("creatorName", p.getCreatorName()); item.put("classes", myClasses);
            result.add(item);
        }
        return Result.success(result);
    }

    // ── updateClassOverride ──
    @Override
    @Transactional
    public Result<?> updateClassOverride(Long planId, Long classId, Map<String, Object> params, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");
        if (params.containsKey("articleTargetOverride")) tpc.setArticleTargetOverride(toInt(params.get("articleTargetOverride"), null));
        if (params.containsKey("wordTargetOverride")) tpc.setWordTargetOverride(toInt(params.get("wordTargetOverride"), null));
        if (params.containsKey("wordCategoryOverride")) tpc.setWordCategoryOverride((String) params.get("wordCategoryOverride"));
        if (params.containsKey("paperTargetOverride")) tpc.setPaperTargetOverride(toInt(params.get("paperTargetOverride"), null));
        if (params.containsKey("articleWeightOverride")) tpc.setArticleWeightOverride(toInt(params.get("articleWeightOverride"), null));
        if (params.containsKey("wordWeightOverride")) tpc.setWordWeightOverride(toInt(params.get("wordWeightOverride"), null));
        if (params.containsKey("paperWeightOverride")) tpc.setPaperWeightOverride(toInt(params.get("paperWeightOverride"), null));
        if (params.containsKey("cycleTypeOverride")) tpc.setCycleTypeOverride((String) params.get("cycleTypeOverride"));
        if (params.containsKey("semesterStartOverride")) tpc.setSemesterStartOverride(params.get("semesterStartOverride") != null ? LocalDate.parse(params.get("semesterStartOverride").toString()) : null);
        if (params.containsKey("semesterEndOverride")) tpc.setSemesterEndOverride(params.get("semesterEndOverride") != null ? LocalDate.parse(params.get("semesterEndOverride").toString()) : null);
        if (params.containsKey("articleEnabledOverride")) tpc.setArticleEnabledOverride(toInt(params.get("articleEnabledOverride"), null));
        if (params.containsKey("wordEnabledOverride")) tpc.setWordEnabledOverride(toInt(params.get("wordEnabledOverride"), null));
        if (params.containsKey("paperEnabledOverride")) tpc.setPaperEnabledOverride(toInt(params.get("paperEnabledOverride"), null));
        tpc.setModifiedBy(teacherId);
        tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已更新");
    }

    // ── optOutClass / optInClass ──
    @Override
    public Result<?> optOutClass(Long planId, Long classId, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");
        tpc.setOptedOut(1); tpc.setModifiedBy(teacherId); tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已退出");
    }

    @Override
    public Result<?> optInClass(Long planId, Long classId, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");
        tpc.setOptedOut(0); tpc.setModifiedBy(teacherId); tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已重新加入");
    }

    // ── getClassProgress (FIXED: real-time from learning_records) ──
    @Override
    public Result<?> getClassProgress(Long planId, Long classId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("班级未关联此计划");
        int[] eff = resolveEffective(plan, tpc);
        boolean artEn = resolveEnabled(plan.getArticleEnabled(), tpc.getArticleEnabledOverride());
        boolean wrdEn = resolveEnabled(plan.getWordEnabled(), tpc.getWordEnabledOverride());
        String cats = wrdEn ? buildCategoryIn(resolveWordCat(plan, tpc)) : null;
        LocalDateTime[] period = resolvePeriod(plan, tpc, LocalDate.now());
        List<StudentClass> scList = studentClassMapper.selectList(
            new LambdaQueryWrapper<StudentClass>().eq(StudentClass::getClassId, classId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentClass sc : scList) {
            Long sid = sc.getStudentId();
            User u = userMapper.selectById(sid);
            String name = (u != null && u.getRealName() != null) ? u.getRealName() : (u != null ? u.getUsername() : "");
            int articles = artEn ? learningRecordMapper.countDistinctTextsRead(sid, period[0], period[1]) : 0;
            int words = (wrdEn && cats != null) ? learningRecordMapper.countDistinctWordsReviewedByCategory(sid, period[0], period[1], cats) : 0;
            BigDecimal score = calculateScore(plan.getTotalScore(), eff[0], eff[1], eff[2], eff[3], eff[4], eff[5], articles, words, 0);
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", sid); m.put("studentName", name);
            m.put("articlesCompleted", articles); m.put("wordsCompleted", words);
            m.put("papersCompleted", 0); m.put("score", score);
            result.add(m);
        }
        return Result.success(result);
    }

    // ── exportClassScore (FIXED: real-time + override) ──
    @Override
    public byte[] exportClassScore(Long planId, Long classId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return new byte[0];
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return new byte[0];
        int[] eff = resolveEffective(plan, tpc);
        boolean artEn = resolveEnabled(plan.getArticleEnabled(), tpc.getArticleEnabledOverride());
        boolean wrdEn = resolveEnabled(plan.getWordEnabled(), tpc.getWordEnabledOverride());
        String cats = wrdEn ? buildCategoryIn(resolveWordCat(plan, tpc)) : null;
        LocalDateTime[] period = resolvePeriod(plan, tpc, LocalDate.now());
        List<StudentClass> scList = studentClassMapper.selectList(
            new LambdaQueryWrapper<StudentClass>().eq(StudentClass::getClassId, classId));
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("成绩");
            CellStyle headerStyle = createHeaderStyle(wb);
            Row header = sheet.createRow(0);
            String[] cols = {"学号", "姓名", "文章完成数", "单词完成数", "真题完成数", "平时分"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (StudentClass sc : scList) {
                Long sid = sc.getStudentId();
                User u = userMapper.selectById(sid);
                String name = (u != null && u.getRealName() != null) ? u.getRealName() : (u != null ? u.getUsername() : "");
                String studentNo = (u != null && u.getUsername() != null) ? u.getUsername() : String.valueOf(sid);
                int articles = artEn ? learningRecordMapper.countDistinctTextsRead(sid, period[0], period[1]) : 0;
                int words = (wrdEn && cats != null) ? learningRecordMapper.countDistinctWordsReviewedByCategory(sid, period[0], period[1], cats) : 0;
                BigDecimal score = calculateScore(plan.getTotalScore(), eff[0], eff[1], eff[2], eff[3], eff[4], eff[5], articles, words, 0);
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(studentNo);
                row.createCell(1).setCellValue(name);
                row.createCell(2).setCellValue(articles);
                row.createCell(3).setCellValue(words);
                row.createCell(4).setCellValue(0);
                row.createCell(5).setCellValue(score.doubleValue());
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("导出Excel失败", e);
            return new byte[0];
        }
    }

    // ── getStudentPlans (FIXED: apply override for targets/weights/semester) ──
    @Override
    public Result<?> getStudentPlans(Long studentId) {
        User student = userMapper.selectById(studentId);
        if (student == null) return Result.error("用户不存在");
        List<StudentClass> scList = studentClassMapper.selectList(
            new LambdaQueryWrapper<StudentClass>().eq(StudentClass::getStudentId, studentId));
        Set<Long> myClassIds = scList.stream().map(StudentClass::getClassId).collect(Collectors.toSet());
        if (myClassIds.isEmpty()) return Result.success(Collections.emptyList());
        List<TaskPlanClass> tpcList = taskPlanClassMapper.selectList(new LambdaQueryWrapper<TaskPlanClass>()
                .in(TaskPlanClass::getClassId, myClassIds).eq(TaskPlanClass::getOptedOut, 0));
        if (tpcList.isEmpty()) return Result.success(Collections.emptyList());
        Set<Long> planIds = tpcList.stream().map(TaskPlanClass::getPlanId).collect(Collectors.toSet());
        List<TaskPlan> plans = taskPlanMapper.selectList(
            new LambdaQueryWrapper<TaskPlan>().in(TaskPlan::getId, planIds).eq(TaskPlan::getStatus, "ACTIVE"));
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskPlan p : plans) {
            enrichPlan(p);
            TaskPlanClass tpc = tpcList.stream()
                .filter(c -> c.getPlanId().equals(p.getId()) && myClassIds.contains(c.getClassId()))
                .findFirst().orElse(null);
            int[] eff = resolveEffective(p, tpc);
            String effCycle = (tpc != null && tpc.getCycleTypeOverride() != null) ? tpc.getCycleTypeOverride() : p.getCycleType();
            String wordCat = resolveWordCat(p, tpc);
            LocalDate effSemStart = (tpc != null && tpc.getSemesterStartOverride() != null) ? tpc.getSemesterStartOverride() : p.getSemesterStart();
            LocalDate effSemEnd = (tpc != null && tpc.getSemesterEndOverride() != null) ? tpc.getSemesterEndOverride() : p.getSemesterEnd();
            boolean artEn = resolveEnabled(p.getArticleEnabled(), tpc != null ? tpc.getArticleEnabledOverride() : null);
            boolean wrdEn = resolveEnabled(p.getWordEnabled(), tpc != null ? tpc.getWordEnabledOverride() : null);
            boolean papEn = resolveEnabled(p.getPaperEnabled(), tpc != null ? tpc.getPaperEnabledOverride() : null);
            LocalDateTime[] period = resolvePeriod(p, tpc, today);
            int articles = artEn ? learningRecordMapper.countDistinctTextsRead(studentId, period[0], period[1]) : 0;
            int words = 0;
            if (wrdEn) {
                String cats = buildCategoryIn(wordCat);
                words = learningRecordMapper.countDistinctWordsReviewedByCategory(studentId, period[0], period[1], cats);
            }
            int papers = 0;
            BigDecimal currentScore = calculateScore(p.getTotalScore(), eff[0], eff[1], eff[2], eff[3], eff[4], eff[5], articles, words, papers);
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId()); item.put("title", p.getTitle());
            item.put("courseName", p.getCourseName()); item.put("cycleType", effCycle);
            item.put("semesterStart", effSemStart); item.put("semesterEnd", effSemEnd);
            item.put("articleTarget", eff[0]); item.put("wordTarget", eff[1]);
            item.put("wordCategory", wordCat); item.put("paperTarget", eff[2]);
            item.put("totalScore", p.getTotalScore());
            item.put("articleEnabled", artEn); item.put("wordEnabled", wrdEn); item.put("paperEnabled", papEn);
            item.put("articleWeight", eff[3]); item.put("wordWeight", eff[4]); item.put("paperWeight", eff[5]);
            item.put("currentArticles", articles); item.put("currentWords", words); item.put("currentPapers", papers);
            item.put("currentScore", currentScore);
            result.add(item);
        }
        return Result.success(result);
    }

    // ── refreshPeriodRecords ──
    @Override
    public void refreshPeriodRecords(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) return;
        logger.info("refreshPeriodRecords called for plan={} (no-op, progress is real-time)", planId);
    }

    // ── helpers ──

    /** Resolve effective [artTarget, wrdTarget, papTarget, artWeight, wrdWeight, papWeight] */
    private int[] resolveEffective(TaskPlan plan, TaskPlanClass tpc) {
        return new int[] {
            resolveInt(tpc != null ? tpc.getArticleTargetOverride() : null, plan.getArticleTarget()),
            resolveInt(tpc != null ? tpc.getWordTargetOverride() : null, plan.getWordTarget()),
            resolveInt(tpc != null ? tpc.getPaperTargetOverride() : null, plan.getPaperTarget()),
            resolveInt(tpc != null ? tpc.getArticleWeightOverride() : null, plan.getArticleWeight()),
            resolveInt(tpc != null ? tpc.getWordWeightOverride() : null, plan.getWordWeight()),
            resolveInt(tpc != null ? tpc.getPaperWeightOverride() : null, plan.getPaperWeight())
        };
    }

    private String resolveWordCat(TaskPlan plan, TaskPlanClass tpc) {
        return (tpc != null && tpc.getWordCategoryOverride() != null) ? tpc.getWordCategoryOverride() : plan.getWordCategory();
    }

    /** Returns [since, until] as LocalDateTime pair for the current period */
    private LocalDateTime[] resolvePeriod(TaskPlan plan, TaskPlanClass tpc, LocalDate today) {
        String effCycle = (tpc != null && tpc.getCycleTypeOverride() != null) ? tpc.getCycleTypeOverride() : plan.getCycleType();
        LocalDate periodStart, periodEnd;
        if ("WEEKLY".equals(effCycle)) {
            periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            periodEnd = periodStart.plusDays(6);
        } else {
            periodStart = today.withDayOfMonth(1);
            periodEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        }
        return new LocalDateTime[] { periodStart.atStartOfDay(), periodEnd.plusDays(1).atStartOfDay() };
    }

    private void enrichPlan(TaskPlan plan) {
        Long count = taskPlanClassMapper.selectCount(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, plan.getId()));
        plan.setClassCount(count.intValue());
        List<TaskPlanClass> tpClasses = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, plan.getId()).last("LIMIT 1"));
        if (!tpClasses.isEmpty()) {
            com.english.entity.Class cls = classMapper.selectById(tpClasses.get(0).getClassId());
            if (cls != null && cls.getName() != null) {
                String n = cls.getName();
                int idx = n.indexOf(" - ");
                plan.setCourseName(idx > 0 ? n.substring(0, idx) : n);
            }
        }
        if (plan.getCreatedBy() != null) {
            User u = userMapper.selectById(plan.getCreatedBy());
            if (u != null) plan.setCreatorName(u.getRealName() != null ? u.getRealName() : u.getUsername());
        }
    }

    private void enrichPlanClass(TaskPlanClass tpc) {
        com.english.entity.Class cls = classMapper.selectById(tpc.getClassId());
        if (cls != null) {
            tpc.setClassName(cls.getName());
            tpc.setTeacherId(cls.getTeacherId());
            if (cls.getTeacherId() != null) {
                User teacher = userMapper.selectById(cls.getTeacherId());
                if (teacher != null) tpc.setTeacherName(teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername());
            }
            if (cls.getSchoolClassId() != null) {
                TeachingClass tc = teachingClassMapper.selectOne(
                    new LambdaQueryWrapper<TeachingClass>().eq(TeachingClass::getClassId, cls.getSchoolClassId()).last("LIMIT 1"));
                if (tc != null) tpc.setCourseSeq(tc.getCourseSequence());
            }
        }
    }

    private TaskPlanClass findPlanClass(Long planId, Long classId) {
        return taskPlanClassMapper.selectOne(
            new LambdaQueryWrapper<TaskPlanClass>()
                .eq(TaskPlanClass::getPlanId, planId)
                .eq(TaskPlanClass::getClassId, classId));
    }

    private BigDecimal calculateScore(BigDecimal totalScore,
            int artTarget, int wrdTarget, int papTarget,
            int artWeight, int wrdWeight, int papWeight,
            int articles, int words, int papers) {
        BigDecimal total = totalScore != null ? totalScore : new BigDecimal("100");
        double aRate = artTarget > 0 ? Math.min(1.0, (double) articles / artTarget) : 0;
        double wRate = wrdTarget > 0 ? Math.min(1.0, (double) words / wrdTarget) : 0;
        double pRate = papTarget > 0 ? Math.min(1.0, (double) papers / papTarget) : 0;
        double score = total.doubleValue() * (
            aRate * artWeight / 100.0 + wRate * wrdWeight / 100.0 + pRate * papWeight / 100.0);
        return new BigDecimal(score).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean resolveEnabled(Integer planLevel, Integer classOverride) {
        if (classOverride != null) return classOverride == 1;
        return planLevel == null || planLevel == 1;
    }

    private int resolveInt(Integer override, int planDefault) {
        return (override != null) ? override : planDefault;
    }

    private String buildCategoryIn(String wordCategory) {
        if ("CET6".equals(wordCategory)) return "'CET6'";
        if ("CET4".equals(wordCategory)) return "'CET4','CET6'";
        return "'GAOKAO','CET4','CET6'";
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        return Long.valueOf(v.toString());
    }

    private Integer toInt(Object v, Integer def) {
        if (v == null) return def;
        return Integer.valueOf(v.toString());
    }
}
