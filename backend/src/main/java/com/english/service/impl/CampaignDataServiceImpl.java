package com.english.service.impl;

import com.english.entity.CampaignManualRecord;
import com.english.entity.CampaignTask;
import com.english.mapper.CampaignManualRecordMapper;
import com.english.mapper.CampaignTaskMapper;
import com.english.service.CampaignDataService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
                    // 读取：学号 | 数值 | 备注
                    String studentId = getCellValueAsString(row.getCell(0));
                    String valueStr = getCellValueAsString(row.getCell(1));
                    String remark = getCellValueAsString(row.getCell(2));

                    if (studentId == null || studentId.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 学号为空");
                        failCount++;
                        continue;
                    }

                    if (valueStr == null || valueStr.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 数值为空");
                        failCount++;
                        continue;
                    }

                    BigDecimal value = new BigDecimal(valueStr);

                    // TODO: 根据学号查找用户ID
                    Long userId = 1L; // 临时：需要实现用户查询

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

        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);
        result.put("batchId", batchId);

        return result;
    }

    @Override
    public byte[] exportCampaignData(Long campaignId, String format) {
        // TODO: 实现活动数据导出
        return new byte[0];
    }

    @Override
    public byte[] exportRankingData(Long campaignId, String dimension, String format) {
        // TODO: 实现排名数据导出
        return new byte[0];
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
