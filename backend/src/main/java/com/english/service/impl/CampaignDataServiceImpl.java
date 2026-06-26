package com.english.service.impl;

import com.english.entity.CampaignManualRecord;
import com.english.entity.CampaignTask;
import com.english.entity.CampaignParticipation;
import com.english.entity.CampaignTaskCompletion;
import com.english.entity.CampaignActivity;
import com.english.entity.User;
import com.english.entity.SchoolStudent;
import com.english.enums.EnrollmentStatus;
import com.english.mapper.CampaignManualRecordMapper;
import com.english.mapper.CampaignTaskMapper;
import com.english.mapper.CampaignParticipationMapper;
import com.english.mapper.CampaignTaskCompletionMapper;
import com.english.mapper.CampaignActivityMapper;
import com.english.mapper.UserMapper;
import com.english.mapper.SchoolStudentMapper;
import com.english.service.CampaignDataService;
import com.english.service.CampaignStatsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 活动数据管理服务实现（简化版）
 */
@Slf4j
@Service
public class CampaignDataServiceImpl implements CampaignDataService {

    @Autowired
    private CampaignManualRecordMapper manualRecordMapper;

    @Autowired
    private CampaignTaskMapper taskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CampaignParticipationMapper participationMapper;

    @Autowired
    private CampaignTaskCompletionMapper completionMapper;

    @Autowired
    private CampaignActivityMapper campaignActivityMapper;

    @Autowired
    private SchoolStudentMapper schoolStudentMapper;

    @Autowired
    private CampaignStatsService campaignStatsService;

    @Override
    public void recordManualData(Long campaignId, Long taskId, Long userId, BigDecimal value, String remark, Long createdBy) {
        CampaignManualRecord record = new CampaignManualRecord();
        record.setCampaignId(campaignId);
        record.setTaskId(taskId);
        record.setUserId(userId);
        record.setRecordValue(value);
        record.setRecordType("MANUAL");
        record.setRemark(remark);
        record.setCreatedBy(createdBy);
        record.setCreatedTime(LocalDateTime.now());

        manualRecordMapper.insert(record);
    }

    @Override
    public Map<String, Object> importFromExcel(Long campaignId, Long taskId, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            int startRow = 1; // 跳过表头

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    // 读取：学号(0) | 姓名(1，可选) | 完成值(2) | 备注(3，可选)
                    String studentId = getCellValueAsString(row.getCell(0));
                    // 姓名列(1)跳过，仅用于人工核对
                    String valueStr = getCellValueAsString(row.getCell(2));
                    String remark = getCellValueAsString(row.getCell(3));

                    if (studentId == null || studentId.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 学号为空");
                        failCount++;
                        continue;
                    }

                    if (valueStr == null || valueStr.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 完成值为空");
                        failCount++;
                        continue;
                    }

                    BigDecimal value = new BigDecimal(valueStr);

                    // 根据学号查找用户ID（学号即 users.username）
                    User user = userMapper.selectActiveByUsername(studentId.trim());
                    if (user == null) {
                        errors.add("第" + (i + 1) + "行: 学号 " + studentId + " 未找到对应用户");
                        failCount++;
                        continue;
                    }
                    Long userId = user.getId();

                    CampaignManualRecord record = new CampaignManualRecord();
                    record.setCampaignId(campaignId);
                    record.setTaskId(taskId);
                    record.setUserId(userId);
                    record.setRecordValue(value);
                    record.setRecordType("IMPORT");
                    record.setImportBatchId(batchId);
                    record.setRemark(remark);
                    record.setCreatedTime(LocalDateTime.now());

                    manualRecordMapper.insert(record);
                    successCount++;

                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                    failCount++;
                }
            }

        } catch (Exception e) {
            log.error("Excel导入失败", e);
            throw new RuntimeException("Excel导入失败: " + e.getMessage());
        }

        result.put("totalRows", successCount + failCount);
        result.put("successRows", successCount);
        result.put("failedRows", failCount);
        result.put("skippedRows", 0); // 当前无跳过逻辑
        result.put("errors", errors);
        result.put("batchId", batchId);

        return result;
    }

    @Override
    public byte[] exportCampaignData(Long campaignId, String format) {
        // 查询活动下所有任务的手动记录 + 完成记录，导出为 Excel
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("活动数据");

            // 表头
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("任务名称");
            header.createCell(3).setCellValue("任务类型");
            header.createCell(4).setCellValue("当前值");
            header.createCell(5).setCellValue("目标值");
            header.createCell(6).setCellValue("已完成次数");
            header.createCell(7).setCellValue("已获积分");
            header.createCell(8).setCellValue("是否完成");
            header.createCell(9).setCellValue("最后更新时间");

            // 查询所有任务
            QueryWrapper<CampaignTask> taskWrapper = new QueryWrapper<>();
            taskWrapper.eq("campaign_id", campaignId);
            List<CampaignTask> tasks = taskMapper.selectList(taskWrapper);
            Map<Long, CampaignTask> taskMap = new HashMap<>();
            for (CampaignTask t : tasks) {
                taskMap.put(t.getId(), t);
            }

            // 查询所有完成记录
            QueryWrapper<CampaignTaskCompletion> compWrapper = new QueryWrapper<>();
            compWrapper.eq("campaign_id", campaignId);
            List<CampaignTaskCompletion> completions = completionMapper.selectList(compWrapper);

            // 批量查询用户信息
            List<Long> userIds = completions.stream()
                    .map(CampaignTaskCompletion::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, User> userMap = new HashMap<>();
            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                for (User u : users) {
                    if (u != null && (u.getDeleted() == null || u.getDeleted() == 0)) {
                        userMap.put(u.getId(), u);
                    }
                }
            }

            int rowIdx = 1;
            for (CampaignTaskCompletion c : completions) {
                Row row = sheet.createRow(rowIdx++);
                User user = userMap.get(c.getUserId());
                CampaignTask task = taskMap.get(c.getTaskId());

                row.createCell(0).setCellValue(user != null ? user.getUsername() : "");
                row.createCell(1).setCellValue(user != null && user.getRealName() != null ? user.getRealName() : "");
                row.createCell(2).setCellValue(task != null ? task.getTaskName() : "");
                row.createCell(3).setCellValue(task != null && task.getTaskType() != null ? task.getTaskType().name() : "");
                row.createCell(4).setCellValue(c.getCurrentValue() != null ? c.getCurrentValue().doubleValue() : 0);
                row.createCell(5).setCellValue(task != null && task.getTargetValue() != null ? task.getTargetValue() : 0);
                row.createCell(6).setCellValue(c.getCompletionCount() != null ? c.getCompletionCount() : 0);
                row.createCell(7).setCellValue(c.getEarnedScore() != null ? c.getEarnedScore() : 0);
                row.createCell(8).setCellValue(c.getIsCompleted() != null && c.getIsCompleted() ? "是" : "否");
                row.createCell(9).setCellValue(c.getLastUpdateTime() != null ? c.getLastUpdateTime().toString() : "");
            }

            // 自动列宽
            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("导出活动数据失败", e);
            throw new RuntimeException("导出活动数据失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] exportRankingData(Long campaignId, String dimension, String format) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("排名数据");
            CampaignActivity campaign = campaignActivityMapper.selectById(campaignId);
            String campaignTitle = campaign != null ? campaign.getTitle() : String.valueOf(campaignId);

            if ("CLASS".equalsIgnoreCase(dimension)) {
                // 班级排名
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("班级");
                header.createCell(1).setCellValue("学院");
                header.createCell(2).setCellValue("总分");
                header.createCell(3).setCellValue("参与人数");
                header.createCell(4).setCellValue("平均分");
                header.createCell(5).setCellValue("完成率");

                List<Map<String, Object>> ranking = campaignStatsService.getRankingByClass(campaignId);
                int rowIdx = 1;
                for (Map<String, Object> item : ranking) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue((String) item.getOrDefault("className", ""));
                    row.createCell(1).setCellValue((String) item.getOrDefault("collegeName", ""));
                    row.createCell(2).setCellValue(((Number) item.getOrDefault("totalScore", 0)).intValue());
                    row.createCell(3).setCellValue(((Number) item.getOrDefault("participantCount", 0)).intValue());
                    row.createCell(4).setCellValue(((Number) item.getOrDefault("avgScore", 0)).doubleValue());
                    row.createCell(5).setCellValue((String) item.getOrDefault("completionRate", "0%"));
                }
            } else if ("COLLEGE".equalsIgnoreCase(dimension)) {
                // 学院排名
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("学院");
                header.createCell(1).setCellValue("总分");
                header.createCell(2).setCellValue("参与人数");
                header.createCell(3).setCellValue("平均分");
                header.createCell(4).setCellValue("完成率");

                List<Map<String, Object>> ranking = campaignStatsService.getRankingByCollege(campaignId);
                int rowIdx = 1;
                for (Map<String, Object> item : ranking) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue((String) item.getOrDefault("collegeName", ""));
                    row.createCell(1).setCellValue(((Number) item.getOrDefault("totalScore", 0)).intValue());
                    row.createCell(2).setCellValue(((Number) item.getOrDefault("participantCount", 0)).intValue());
                    row.createCell(3).setCellValue(((Number) item.getOrDefault("avgScore", 0)).doubleValue());
                    row.createCell(4).setCellValue((String) item.getOrDefault("completionRate", "0%"));
                }
            } else {
                // 个人排名（默认）
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("排名");
                header.createCell(1).setCellValue("学号");
                header.createCell(2).setCellValue("姓名");
                header.createCell(3).setCellValue("班级");
                header.createCell(4).setCellValue("学院");
                header.createCell(5).setCellValue("总分");
                header.createCell(6).setCellValue("完成任务数");
                header.createCell(7).setCellValue("完成状态");

                // 查询所有已报名参与者，按总分降序
                QueryWrapper<CampaignParticipation> pWrapper = new QueryWrapper<>();
                pWrapper.eq("campaign_id", campaignId);
                pWrapper.eq("enrollment_status", EnrollmentStatus.ENROLLED);
                pWrapper.orderByDesc("total_score", "completed_tasks", "id");
                List<CampaignParticipation> participations = participationMapper.selectList(pWrapper);

                // 批量查询用户
                List<Long> userIds = participations.stream()
                        .map(CampaignParticipation::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                Map<Long, User> userMap = new HashMap<>();
                if (!userIds.isEmpty()) {
                    List<User> users = userMapper.selectBatchIds(userIds);
                    for (User u : users) {
                        if (u != null && (u.getDeleted() == null || u.getDeleted() == 0)) {
                            userMap.put(u.getId(), u);
                        }
                    }
                }

                // 批量查询学校学生信息（班级、学院）
                List<Long> schoolStudentIds = userMap.values().stream()
                        .map(User::getSchoolStudentId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                Map<Long, SchoolStudent> schoolStudentMap = new HashMap<>();
                if (!schoolStudentIds.isEmpty()) {
                    List<SchoolStudent> schoolStudents = schoolStudentMapper.selectBatchIds(schoolStudentIds);
                    for (SchoolStudent ss : schoolStudents) {
                        if (ss != null) {
                            schoolStudentMap.put(ss.getId(), ss);
                        }
                    }
                }

                int rowIdx = 1;
                int rank = 0;
                Integer prevScore = null;
                for (int i = 0; i < participations.size(); i++) {
                    CampaignParticipation p = participations.get(i);
                    Integer score = p.getTotalScore();
                    int scoreVal = score != null ? score : 0;
                    int prevVal = prevScore != null ? prevScore : 0;
                    if (prevScore == null || prevVal != scoreVal) {
                        rank = i + 1;
                        prevScore = score;
                    }
                    Row row = sheet.createRow(rowIdx++);
                    User user = userMap.get(p.getUserId());
                    SchoolStudent schoolStudent = user != null && user.getSchoolStudentId() != null
                            ? schoolStudentMap.get(user.getSchoolStudentId())
                            : null;

                    row.createCell(0).setCellValue(rank);
                    row.createCell(1).setCellValue(user != null ? user.getUsername() : "");
                    row.createCell(2).setCellValue(user != null && user.getRealName() != null ? user.getRealName() : "");
                    row.createCell(3).setCellValue(schoolStudent != null && schoolStudent.getClassName() != null ? schoolStudent.getClassName() : "");
                    row.createCell(4).setCellValue(schoolStudent != null && schoolStudent.getCollegeName() != null ? schoolStudent.getCollegeName() : "");
                    row.createCell(5).setCellValue(scoreVal);
                    row.createCell(6).setCellValue(p.getCompletedTasks() != null ? p.getCompletedTasks() : 0);
                    row.createCell(7).setCellValue(p.getCompletionStatus() != null ? p.getCompletionStatus() : "");
                }
            }

            // 添加活动标题到首行注释
            sheet.createFreezePane(0, 1);

            for (int i = 0; i < 8; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("导出排名数据失败", e);
            throw new RuntimeException("导出排名数据失败: " + e.getMessage());
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 保留小数精度，避免 (long) 强转丢失小数（如 30.5 → 30）
                // BigDecimal.valueOf(double) 能精确表示 double 的短小数形式
                return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // 公式单元格：取缓存值，按结果类型递归解析
                CellType cached = cell.getCachedFormulaResultType();
                if (cached == CellType.NUMERIC) {
                    return java.math.BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                } else if (cached == CellType.STRING) {
                    return cell.getStringCellValue();
                } else if (cached == CellType.BOOLEAN) {
                    return String.valueOf(cell.getBooleanCellValue());
                }
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
