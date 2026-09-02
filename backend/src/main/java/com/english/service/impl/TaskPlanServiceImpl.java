package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Override
    public Result<?> listPlans(Map<String, Object> params) {
        LambdaQueryWrapper<TaskPlan> qw = new LambdaQueryWrapper<>();
        if (params != null && params.get("status") != null) {
            qw.eq(TaskPlan::getStatus, params.get("status"));
        }
        qw.orderByDesc(TaskPlan::getCreatedTime);
        List<TaskPlan> list = taskPlanMapper.selectList(qw);
        for (TaskPlan p : list) {
            enrichPlan(p);
        }
        return Result.success(list);
    }

    @Override
    public Result<?> getPlanDetail(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        enrichPlan(plan);
        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, planId));
        for (TaskPlanClass tpc : classes) {
            enrichPlanClass(tpc);
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("plan", plan);
        detail.put("classes", classes);
        return Result.success(detail);
    }

    @Override
    public Result<?> getPlanStats(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");
        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>().eq(TaskPlanClass::getPlanId, planId).eq(TaskPlanClass::getOptedOut, 0));
        List<TaskPlanRecord> allRecords = taskPlanRecordMapper.selectList(
            new LambdaQueryWrapper<TaskPlanRecord>().eq(TaskPlanRecord::getPlanId, planId));

        int totalStudents = 0;
        long sumArticles = 0, sumWords = 0, sumPapers = 0;
        List<Map<String, Object>> classDetails = new ArrayList<>();

        for (TaskPlanClass tpc : classes) {
            enrichPlanClass(tpc);
            List<TaskPlanRecord> classRecords = allRecords.stream()
                .filter(r -> r.getClassId().equals(tpc.getClassId())).collect(Collectors.toList());
            Set<Long> students = classRecords.stream().map(TaskPlanRecord::getStudentId).collect(Collectors.toSet());
            int sc = students.size();
            totalStudents += sc;
            int ca = classRecords.stream().mapToInt(r -> r.getArticlesCompleted() != null ? r.getArticlesCompleted() : 0).sum();
            int cw = classRecords.stream().mapToInt(r -> r.getWordsCompleted() != null ? r.getWordsCompleted() : 0).sum();
            int cp = classRecords.stream().mapToInt(r -> r.getPapersCompleted() != null ? r.getPapersCompleted() : 0).sum();
            sumArticles += ca; sumWords += cw; sumPapers += cp;

            int at = tpc.getArticleTargetOverride() != null ? tpc.getArticleTargetOverride() : plan.getArticleTarget();
            int wt = tpc.getWordTargetOverride() != null ? tpc.getWordTargetOverride() : plan.getWordTarget();
            int pt = tpc.getPaperTargetOverride() != null ? tpc.getPaperTargetOverride() : plan.getPaperTarget();

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

    @Override
    public byte[] exportPlanScore(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return new byte[0];
        List<TaskPlanRecord> records = taskPlanRecordMapper.selectList(
            new LambdaQueryWrapper<TaskPlanRecord>().eq(TaskPlanRecord::getPlanId, planId));
        return buildCsv(plan, records);
    }

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
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("courseName", p.getCourseName());
            item.put("cycleType", p.getCycleType());
            item.put("semesterStart", p.getSemesterStart());
            item.put("semesterEnd", p.getSemesterEnd());
            item.put("articleTarget", p.getArticleTarget());
            item.put("wordTarget", p.getWordTarget());
            item.put("wordCategory", p.getWordCategory());
            item.put("paperTarget", p.getPaperTarget());
            item.put("articleWeight", p.getArticleWeight());
            item.put("wordWeight", p.getWordWeight());
            item.put("paperWeight", p.getPaperWeight());
            item.put("totalScore", p.getTotalScore());
            item.put("articleEnabled", p.getArticleEnabled());
            item.put("wordEnabled", p.getWordEnabled());
            item.put("paperEnabled", p.getPaperEnabled());
            item.put("status", p.getStatus());
            item.put("creatorName", p.getCreatorName());
            item.put("classes", myClasses);
            result.add(item);
        }
        return Result.success(result);
    }

    @Override
    @Transactional
    public Result<?> updateClassOverride(Long planId, Long classId, Map<String, Object> params, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");

        if (params.containsKey("articleTargetOverride"))
            tpc.setArticleTargetOverride(toInt(params.get("articleTargetOverride"), null));
        if (params.containsKey("wordTargetOverride"))
            tpc.setWordTargetOverride(toInt(params.get("wordTargetOverride"), null));
        if (params.containsKey("wordCategoryOverride"))
            tpc.setWordCategoryOverride((String) params.get("wordCategoryOverride"));
        if (params.containsKey("paperTargetOverride"))
            tpc.setPaperTargetOverride(toInt(params.get("paperTargetOverride"), null));
        if (params.containsKey("articleWeightOverride"))
            tpc.setArticleWeightOverride(toInt(params.get("articleWeightOverride"), null));
        if (params.containsKey("wordWeightOverride"))
            tpc.setWordWeightOverride(toInt(params.get("wordWeightOverride"), null));
        if (params.containsKey("paperWeightOverride"))
            tpc.setPaperWeightOverride(toInt(params.get("paperWeightOverride"), null));
        if (params.containsKey("cycleTypeOverride"))
            tpc.setCycleTypeOverride((String) params.get("cycleTypeOverride"));
        if (params.containsKey("semesterStartOverride"))
            tpc.setSemesterStartOverride(params.get("semesterStartOverride") != null ? LocalDate.parse(params.get("semesterStartOverride").toString()) : null);
        if (params.containsKey("semesterEndOverride"))
            tpc.setSemesterEndOverride(params.get("semesterEndOverride") != null ? LocalDate.parse(params.get("semesterEndOverride").toString()) : null);
        if (params.containsKey("articleEnabledOverride"))
            tpc.setArticleEnabledOverride(toInt(params.get("articleEnabledOverride"), null));
        if (params.containsKey("wordEnabledOverride"))
            tpc.setWordEnabledOverride(toInt(params.get("wordEnabledOverride"), null));
        if (params.containsKey("paperEnabledOverride"))
            tpc.setPaperEnabledOverride(toInt(params.get("paperEnabledOverride"), null));
        tpc.setModifiedBy(teacherId);
        tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已更新");
    }

    @Override
    public Result<?> optOutClass(Long planId, Long classId, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");
        tpc.setOptedOut(1);
        tpc.setModifiedBy(teacherId);
        tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已退出");
    }

    @Override
    public Result<?> optInClass(Long planId, Long classId, Long teacherId) {
        TaskPlanClass tpc = findPlanClass(planId, classId);
        if (tpc == null) return Result.error("关联不存在");
        com.english.entity.Class cls = classMapper.selectById(classId);
        if (cls == null || !teacherId.equals(cls.getTeacherId())) return Result.error("无权限操作此班级");
        tpc.setOptedOut(0);
        tpc.setModifiedBy(teacherId);
        tpc.setModifiedTime(LocalDateTime.now());
        taskPlanClassMapper.updateById(tpc);
        return Result.success("已重新加入");
    }

    @Override
    public Result<?> getClassProgress(Long planId, Long classId) {
        List<TaskPlanRecord> records = taskPlanRecordMapper.selectList(
            new LambdaQueryWrapper<TaskPlanRecord>()
                .eq(TaskPlanRecord::getPlanId, planId)
                .eq(TaskPlanRecord::getClassId, classId));

        Map<Long, Map<String, Object>> studentMap = new LinkedHashMap<>();
        for (TaskPlanRecord r : records) {
            studentMap.computeIfAbsent(r.getStudentId(), k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("studentId", k);
                m.put("studentName", "");
                m.put("articlesCompleted", 0);
                m.put("wordsCompleted", 0);
                m.put("papersCompleted", 0);
                return m;
            });
            Map<String, Object> m = studentMap.get(r.getStudentId());
            m.put("articlesCompleted", (int) m.get("articlesCompleted") + (r.getArticlesCompleted() != null ? r.getArticlesCompleted() : 0));
            m.put("wordsCompleted", (int) m.get("wordsCompleted") + (r.getWordsCompleted() != null ? r.getWordsCompleted() : 0));
            m.put("papersCompleted", (int) m.get("papersCompleted") + (r.getPapersCompleted() != null ? r.getPapersCompleted() : 0));
        }

        for (Map<String, Object> m : studentMap.values()) {
            User u = userMapper.selectById((Long) m.get("studentId"));
            if (u != null) m.put("studentName", u.getRealName() != null ? u.getRealName() : u.getUsername());
        }

        return Result.success(new ArrayList<>(studentMap.values()));
    }

    @Override
    public byte[] exportClassScore(Long planId, Long classId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null) return new byte[0];
        List<TaskPlanRecord> records = taskPlanRecordMapper.selectList(
            new LambdaQueryWrapper<TaskPlanRecord>()
                .eq(TaskPlanRecord::getPlanId, planId)
                .eq(TaskPlanRecord::getClassId, classId));
        return buildCsv(plan, records);
    }

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

            String effectiveCycle = (tpc != null && tpc.getCycleTypeOverride() != null) ? tpc.getCycleTypeOverride() : p.getCycleType();
            LocalDate periodStart, periodEnd;
            if ("WEEKLY".equals(effectiveCycle)) {
                periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                periodEnd = periodStart.plusDays(6);
            } else {
                periodStart = today.withDayOfMonth(1);
                periodEnd = today.with(TemporalAdjusters.lastDayOfMonth());
            }
            LocalDateTime since = periodStart.atStartOfDay();
            LocalDateTime until = periodEnd.plusDays(1).atStartOfDay();

            boolean artEnabled = resolveEnabled(p.getArticleEnabled(), tpc != null ? tpc.getArticleEnabledOverride() : null);
            boolean wrdEnabled = resolveEnabled(p.getWordEnabled(), tpc != null ? tpc.getWordEnabledOverride() : null);
            boolean papEnabled = resolveEnabled(p.getPaperEnabled(), tpc != null ? tpc.getPaperEnabledOverride() : null);

            int articles = artEnabled ? learningRecordMapper.countDistinctTextsRead(studentId, since, until) : 0;
            int papers = 0;

            String wordCat = (tpc != null && tpc.getWordCategoryOverride() != null) ? tpc.getWordCategoryOverride() : p.getWordCategory();
            int words = 0;
            if (wrdEnabled) {
                String cats = buildCategoryIn(wordCat);
                words = learningRecordMapper.countDistinctWordsReviewedByCategory(studentId, since, until, cats);
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("courseName", p.getCourseName());
            item.put("cycleType", effectiveCycle);
            item.put("semesterStart", p.getSemesterStart());
            item.put("semesterEnd", p.getSemesterEnd());
            item.put("articleTarget", p.getArticleTarget());
            item.put("wordTarget", p.getWordTarget());
            item.put("wordCategory", wordCat);
            item.put("paperTarget", p.getPaperTarget());
            item.put("totalScore", p.getTotalScore());
            item.put("articleEnabled", artEnabled);
            item.put("wordEnabled", wrdEnabled);
            item.put("paperEnabled", papEnabled);
            item.put("currentArticles", articles);
            item.put("currentWords", words);
            item.put("currentPapers", papers);
            result.add(item);
        }
        return Result.success(result);
    }

    @Override
    public void refreshPeriodRecords(Long planId) {
        TaskPlan plan = taskPlanMapper.selectById(planId);
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) return;

        LocalDate today = LocalDate.now();
        LocalDate periodStart, periodEnd;
        if ("WEEKLY".equals(plan.getCycleType())) {
            periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            periodEnd = periodStart.plusDays(6);
        } else {
            periodStart = today.withDayOfMonth(1);
            periodEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        }

        List<TaskPlanClass> classes = taskPlanClassMapper.selectList(
            new LambdaQueryWrapper<TaskPlanClass>()
                .eq(TaskPlanClass::getPlanId, planId).eq(TaskPlanClass::getOptedOut, 0));

        for (TaskPlanClass tpc : classes) {
            List<StudentClass> studentClasses = null;
            // TODO: query students in class from student_class table
            // For now, use records that already exist
            logger.info("Refreshing records for plan={} class={} period={} to {}", planId, tpc.getClassId(), periodStart, periodEnd);
        }
    }

    // --- helpers ---

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

    private byte[] buildCsv(TaskPlan plan, List<TaskPlanRecord> records) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // UTF-8 BOM
            PrintWriter pw = new PrintWriter(baos);
            pw.println("学号,姓名,文章完成数,单词完成数,真题完成数,平时分");

            Map<Long, int[]> studentTotals = new LinkedHashMap<>();
            for (TaskPlanRecord r : records) {
                studentTotals.computeIfAbsent(r.getStudentId(), k -> new int[3]);
                int[] t = studentTotals.get(r.getStudentId());
                t[0] += r.getArticlesCompleted() != null ? r.getArticlesCompleted() : 0;
                t[1] += r.getWordsCompleted() != null ? r.getWordsCompleted() : 0;
                t[2] += r.getPapersCompleted() != null ? r.getPapersCompleted() : 0;
            }

            for (Map.Entry<Long, int[]> entry : studentTotals.entrySet()) {
                User u = userMapper.selectById(entry.getKey());
                String name = u != null ? (u.getRealName() != null ? u.getRealName() : u.getUsername()) : "";
                String studentNo = u != null && u.getUsername() != null ? u.getUsername() : String.valueOf(entry.getKey());
                int[] t = entry.getValue();
                BigDecimal score = calculateScore(plan, t[0], t[1], t[2]);
                pw.println(String.join(",", studentNo, name, String.valueOf(t[0]), String.valueOf(t[1]), String.valueOf(t[2]), score.toPlainString()));
            }
            pw.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("导出CSV失败", e);
            return new byte[0];
        }
    }

    private BigDecimal calculateScore(TaskPlan plan, int articles, int words, int papers) {
        BigDecimal total = plan.getTotalScore() != null ? plan.getTotalScore() : new BigDecimal("100");
        double aRate = plan.getArticleTarget() > 0 ? Math.min(1.0, (double) articles / plan.getArticleTarget()) : 0;
        double wRate = plan.getWordTarget() > 0 ? Math.min(1.0, (double) words / plan.getWordTarget()) : 0;
        double pRate = plan.getPaperTarget() > 0 ? Math.min(1.0, (double) papers / plan.getPaperTarget()) : 0;
        double score = total.doubleValue() * (
            aRate * plan.getArticleWeight() / 100.0 +
            wRate * plan.getWordWeight() / 100.0 +
            pRate * plan.getPaperWeight() / 100.0
        );
        return new BigDecimal(score).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean resolveEnabled(Integer planLevel, Integer classOverride) {
        if (classOverride != null) return classOverride == 1;
        return planLevel == null || planLevel == 1;
    }

    private String buildCategoryIn(String wordCategory) {
        if ("CET6".equals(wordCategory)) return "'CET6'";
        if ("CET4".equals(wordCategory)) return "'CET4','CET6'";
        return "'GAOKAO','CET4','CET6'";
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
