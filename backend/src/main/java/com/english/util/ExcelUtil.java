package com.english.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Excel工具类 - 用于Excel文件的导入导出
 */
public class ExcelUtil {

    /**
     * 读取Excel文件，返回List<Map<String, String>>
     *
     * @param file Excel文件
     * @return List<Map<String, String>> 每一行数据作为一个Map
     * @throws IOException IO异常
     */
    public static List<Map<String, String>> readExcel(MultipartFile file) throws IOException {
        List<Map<String, String>> dataList = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // 读取第一个sheet

            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return dataList;
            }

            // 读取表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return dataList;
            }

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }

            // 读取数据行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    String value = cell == null ? "" : getCellValueAsString(cell);
                    rowData.put(headers.get(j), value);
                }

                dataList.add(rowData);
            }
        }

        return dataList;
    }

    /**
     * 获取单元格的值（转为字符串）
     *
     * @param cell 单元格
     * @return 单元格的字符串值
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 避免科学计数法
                    double value = cell.getNumericCellValue();
                    if (value == (long) value) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * 验证Excel文件格式
     *
     * @param file 上传的文件
     * @return 是否为Excel文件
     */
    public static boolean isExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        return filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls");
    }
}
