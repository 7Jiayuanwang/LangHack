package com.english.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.CourseExemptionApplication;
import com.english.mapper.CourseExemptionApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 大学英语免修/免考教师审批。
 * 条件与折算规则依据《内蒙古农业大学关于大学英语课程免修、免考实施细则（试行）》
 * （内农大教字〔2025〕18 号）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseExemptionService {

    /** 雅思/托福成绩有效期（细则：获得时间未超出 2 年）。 */
    private static final int LANGUAGE_CERT_VALID_YEARS = 2;

    private final CourseExemptionApplicationMapper mapper;

    public Result<?> list(String status, long page, long size) {
        String normalized = normalizeStatus(status);
        Page<Map<String, Object>> p = new Page<>(Math.max(1, page), Math.min(100, Math.max(1, size)));
        IPage<Map<String, Object>> result = mapper.selectWithStudent(p, normalized);
        for (Map<String, Object> row : result.getRecords()) {
            annotateEligibility(row);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("records", result.getRecords());
        body.put("total", result.getTotal());
        body.put("page", result.getCurrent());
        body.put("size", result.getSize());
        body.put("counts", countByStatus());
        return Result.success(body);
    }

    public Result<?> detail(Long id) {
        CourseExemptionApplication app = mapper.selectById(id);
        if (app == null) return Result.error("申请不存在");
        fillConvertedScore(app);
        return Result.success(app);
    }

    /** 折算成绩落库前兜底：老数据或学生端提交时未计算的情况。 */
    private void fillConvertedScore(CourseExemptionApplication app) {
        if (app.getConvertedScore() == null) {
            app.setConvertedScore(convertedScore(app.getExamType(), app.getExamScore()));
        }
    }

    @Transactional
    public Result<?> review(Long id, Long teacherId, boolean approve, String comment) {
        CourseExemptionApplication app = mapper.selectById(id);
        if (app == null) return Result.error("申请不存在");
        if (!CourseExemptionApplication.STATUS_PENDING.equals(app.getStatus())) {
            return Result.error("该申请已审批完成，不能重复处理");
        }
        String trimmed = comment == null ? null : comment.trim();
        if (!approve && (trimmed == null || trimmed.isEmpty())) {
            return Result.error("驳回时请填写审批意见");
        }
        app.setStatus(approve ? CourseExemptionApplication.STATUS_APPROVED : CourseExemptionApplication.STATUS_REJECTED);
        app.setReviewComment(trimmed);
        app.setReviewedBy(teacherId);
        app.setReviewedTime(java.time.LocalDateTime.now());
        // 审批时把折算成绩固化到库里，避免列表靠临时计算、导出时为空
        fillConvertedScore(app);
        mapper.updateById(app);
        log.info("免修申请 {} 审批结果: {}，教师 {}", id, app.getStatus(), teacherId);
        return Result.success(approve ? "已通过" : "已驳回", app);
    }

    /** 教师端首页角标使用。 */
    public Result<?> stats() {
        Map<String, Object> counts = countByStatus();
        Map<String, Object> body = new HashMap<>();
        body.put("pending", counts.get("PENDING"));
        body.put("approved", counts.get("APPROVED"));
        body.put("rejected", counts.get("REJECTED"));
        body.put("total", counts.get("ALL"));
        return Result.success(body);
    }

    private Map<String, Object> countByStatus() {
        Map<String, Object> counts = new LinkedHashMap<>();
        long all = 0;
        for (String s : new String[]{CourseExemptionApplication.STATUS_PENDING,
                CourseExemptionApplication.STATUS_APPROVED, CourseExemptionApplication.STATUS_REJECTED}) {
            long c = mapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CourseExemptionApplication>()
                    .eq("status", s));
            counts.put(s, c);
            all += c;
        }
        counts.put("ALL", all);
        return counts;
    }

    /** 附上「成绩是否符合细则申请条件」的判定，供教师核对。 */
    private void annotateEligibility(Map<String, Object> row) {
        String examType = String.valueOf(row.get("examType"));
        Object rawScore = row.get("examScore");
        Object rawDate = row.get("examDate");
        LocalDate examDate = toLocalDate(rawDate);

        Map<String, Object> check = new HashMap<>();
        String reason = eligibilityProblem(examType, rawScore == null ? "" : rawScore.toString(), examDate);
        check.put("eligible", reason == null);
        check.put("problem", reason);
        check.put("scoreValidWithinTwoYears", isWithinValidYears(examType, examDate));
        row.put("check", check);
        if (row.get("convertedScore") == null && reason == null) {
            row.put("convertedScore", convertedScore(examType, rawScore.toString()));
        }
    }

    /**
     * 细则「成绩认定对照表」：
     * 四级 = 成绩*100/710+15；六级 = 成绩*100/710+25；四舍五入取整；
     * 雅思 6/6.5/7/7.5/≥8 → 80/85/90/95/100；
     * 托福 68-79/80-91/92-102/103-112/≥113 → 80/85/90/95/100；
     * 折算后大于 100 按 100 计。不满足申请条件返回 null。
     */
    public static Integer convertedScore(String examType, String scoreText) {
        BigDecimal score;
        try {
            score = new BigDecimal(scoreText == null ? "" : scoreText.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        Integer result;
        if (CourseExemptionApplication.EXAM_CET4.equals(examType)) {
            if (score.compareTo(BigDecimal.valueOf(497)) < 0) return null;
            result = round(score.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(710), 4, RoundingMode.HALF_UP)
                    .add(BigDecimal.valueOf(15)));
        } else if (CourseExemptionApplication.EXAM_CET6.equals(examType)) {
            if (score.compareTo(BigDecimal.valueOf(425)) < 0) return null;
            result = round(score.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(710), 4, RoundingMode.HALF_UP)
                    .add(BigDecimal.valueOf(25)));
        } else if (CourseExemptionApplication.EXAM_IELTS.equals(examType)) {
            if (score.compareTo(BigDecimal.valueOf(6)) < 0) return null;
            if (score.compareTo(BigDecimal.valueOf(8)) >= 0) result = 100;
            else if (score.compareTo(BigDecimal.valueOf(7.5)) >= 0) result = 95;
            else if (score.compareTo(BigDecimal.valueOf(7)) >= 0) result = 90;
            else if (score.compareTo(BigDecimal.valueOf(6.5)) >= 0) result = 85;
            else result = 80;
        } else if (CourseExemptionApplication.EXAM_TOEFL.equals(examType)) {
            if (score.compareTo(BigDecimal.valueOf(68)) < 0) return null;
            if (score.compareTo(BigDecimal.valueOf(113)) >= 0) result = 100;
            else if (score.compareTo(BigDecimal.valueOf(103)) >= 0) result = 95;
            else if (score.compareTo(BigDecimal.valueOf(92)) >= 0) result = 90;
            else if (score.compareTo(BigDecimal.valueOf(80)) >= 0) result = 85;
            else result = 80;
        } else {
            return null;
        }
        return Math.min(100, result);
    }

    private static Integer round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String eligibilityProblem(String examType, String scoreText, LocalDate examDate) {
        if (!isSupportedExam(examType)) return "考试类型不支持";
        BigDecimal score;
        try {
            score = new BigDecimal(scoreText.trim());
        } catch (NumberFormatException e) {
            return "成绩格式不正确";
        }
        if (CourseExemptionApplication.EXAM_IELTS.equals(examType)
                || CourseExemptionApplication.EXAM_TOEFL.equals(examType)) {
            if (examDate == null) return "请填写成绩获得时间";
            if (examDate.plusYears(LANGUAGE_CERT_VALID_YEARS).isBefore(LocalDate.now())) {
                return "成绩已超出两年有效期";
            }
        }
        if (CourseExemptionApplication.EXAM_CET4.equals(examType) && score.compareTo(BigDecimal.valueOf(497)) < 0) {
            return "四级成绩未达到 497 分";
        }
        if (CourseExemptionApplication.EXAM_CET6.equals(examType) && score.compareTo(BigDecimal.valueOf(425)) < 0) {
            return "六级成绩未达到 425 分";
        }
        if (CourseExemptionApplication.EXAM_IELTS.equals(examType) && score.compareTo(BigDecimal.valueOf(6)) < 0) {
            return "雅思成绩未达到 6 分";
        }
        if (CourseExemptionApplication.EXAM_TOEFL.equals(examType) && score.compareTo(BigDecimal.valueOf(68)) < 0) {
            return "托福成绩未达到 68 分";
        }
        return null;
    }

    private boolean isWithinValidYears(String examType, LocalDate examDate) {
        if (!CourseExemptionApplication.EXAM_IELTS.equals(examType)
                && !CourseExemptionApplication.EXAM_TOEFL.equals(examType)) {
            return true;
        }
        return examDate != null && !examDate.plusYears(LANGUAGE_CERT_VALID_YEARS).isBefore(LocalDate.now());
    }

    private boolean isSupportedExam(String examType) {
        return CourseExemptionApplication.EXAM_CET4.equals(examType)
                || CourseExemptionApplication.EXAM_CET6.equals(examType)
                || CourseExemptionApplication.EXAM_IELTS.equals(examType)
                || CourseExemptionApplication.EXAM_TOEFL.equals(examType);
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.time.LocalDateTime dt) return dt.toLocalDate();
        try {
            return LocalDate.parse(value.toString().substring(0, 10));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private String normalizeStatus(String status) {
        if (status == null) return "";
        switch (status.trim().toUpperCase()) {
            case "PENDING": return CourseExemptionApplication.STATUS_PENDING;
            case "APPROVED": return CourseExemptionApplication.STATUS_APPROVED;
            case "REJECTED": return CourseExemptionApplication.STATUS_REJECTED;
            default: return "";
        }
    }
}
