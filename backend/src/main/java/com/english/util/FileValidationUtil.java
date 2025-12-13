package com.english.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件验证工具类 - 提供文件安全验证功能
 */
public class FileValidationUtil {

    // 文件大小限制：10MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Excel文件的magic number（文件头标识）
    private static final byte[] XLSX_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK.. (ZIP格式)
    private static final byte[] XLS_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                                              (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    /**
     * 验证文件大小是否在允许范围内
     *
     * @param file 上传的文件
     * @return 是否通过验证
     */
    public static boolean validateFileSize(MultipartFile file) {
        if (file == null) {
            return false;
        }
        return file.getSize() <= MAX_FILE_SIZE;
    }

    /**
     * 验证文件名是否安全（防止路径遍历攻击）
     *
     * @param filename 文件名
     * @return 是否安全
     */
    public static boolean validateFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含路径遍历字符
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }

        // 检查是否包含特殊字符（只允许字母、数字、下划线、中划线、点号、中文）
        if (!filename.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9._-]+$")) {
            return false;
        }

        return true;
    }

    /**
     * 清理文件名中的危险字符
     *
     * @param filename 原始文件名
     * @return 清理后的文件名
     */
    public static String sanitizeFileName(String filename) {
        if (filename == null) {
            return "";
        }

        // 移除路径分隔符
        filename = filename.replace("/", "_").replace("\\", "_");

        // 移除路径遍历字符
        filename = filename.replace("..", "_");

        // 只保留安全字符
        filename = filename.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9._-]", "_");

        return filename;
    }

    /**
     * 验证文件类型（通过magic number）
     *
     * @param file 上传的文件
     * @return 是否为有效的Excel文件
     * @throws IOException IO异常
     */
    public static boolean validateExcelFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 验证文件扩展名
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        String extension = getFileExtension(filename).toLowerCase();
        if (!extension.equals("xlsx") && !extension.equals("xls")) {
            return false;
        }

        // 验证文件的magic number
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = inputStream.read(header);

            if (bytesRead < 4) {
                return false;
            }

            // 检查XLSX格式（ZIP格式，PK开头）
            if (extension.equals("xlsx")) {
                return matchesMagicNumber(header, XLSX_MAGIC);
            }

            // 检查XLS格式（OLE2格式）
            if (extension.equals("xls")) {
                return matchesMagicNumber(header, XLS_MAGIC);
            }
        }

        return false;
    }

    /**
     * 比较文件头是否匹配magic number
     *
     * @param header 文件头字节
     * @param magic magic number
     * @return 是否匹配
     */
    private static boolean matchesMagicNumber(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }

        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（不含点号）
     */
    private static String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 综合验证上传的Excel文件
     *
     * @param file 上传的文件
     * @return 验证结果信息
     */
    public static ValidationResult validateUploadedExcelFile(MultipartFile file) {
        // 检查文件是否为空
        if (file == null || file.isEmpty()) {
            return ValidationResult.failure("文件不能为空");
        }

        // 检查文件大小
        if (!validateFileSize(file)) {
            return ValidationResult.failure("文件大小超过限制（最大10MB）");
        }

        // 检查文件名安全性
        String filename = file.getOriginalFilename();
        if (!validateFileName(filename)) {
            return ValidationResult.failure("文件名包含非法字符或路径遍历标识");
        }

        // 检查文件类型（通过magic number）
        try {
            if (!validateExcelFile(file)) {
                return ValidationResult.failure("文件类型不正确，请上传有效的Excel文件（.xlsx或.xls）");
            }
        } catch (IOException e) {
            return ValidationResult.failure("文件读取失败：" + e.getMessage());
        }

        return ValidationResult.success();
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "验证通过");
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
