package com.english.controller;

import com.english.dto.Result;
import com.english.entity.SystemConfig;
import com.english.entity.User;
import com.english.service.SystemConfigService;
import com.english.service.UserService;
import com.english.util.SizeParseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器 - 运维面板动态配置
 */
@RestController
@RequestMapping("/api/ops/system-config")
public class SystemConfigController {

    private static final String GROUP_CONTENT = "content_limits";
    private static final java.util.regex.Pattern SIZE_PATTERN =
            java.util.regex.Pattern.compile("^\\d+(\\.\\d+)?[KMG]B$", java.util.regex.Pattern.CASE_INSENSITIVE);

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private UserService userService;

    // ── 通用上传拦截器限制（已有接口，保留） ───────────────────────────────────

    @GetMapping("/upload-limits")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getUploadLimits() {
        Map<String, Object> limits = new HashMap<>();
        SystemConfig globalLimit = systemConfigService.getByKey("upload.max_file_size.global");
        limits.put("globalMaxFileSize", globalLimit != null ? globalLimit.getConfigValue() : "10MB");
        List<SystemConfig> userLimits = systemConfigService.listByGroup("upload.max_file_size.user");
        Map<String, String> userLimitMap = new HashMap<>();
        for (SystemConfig config : userLimits) {
            String username = config.getConfigKey().replace("upload.max_file_size.user.", "");
            userLimitMap.put(username, config.getConfigValue());
        }
        limits.put("userSpecificLimits", userLimitMap);
        return Result.success(limits);
    }

    @PutMapping("/upload-limits/global")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateGlobalUploadLimit(@RequestBody Map<String, String> request) {
        String maxSize = request.get("maxFileSize");
        if (!isValidSize(maxSize)) return Result.error("格式错误，示例：10MB、100KB");
        systemConfigService.upsert("security", "upload.max_file_size.global", maxSize, "全局文件上传大小限制");
        return Result.success("已更新全局上传限制", null);
    }

    @PutMapping("/upload-limits/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateUserUploadLimit(@PathVariable String username, @RequestBody Map<String, String> request) {
        String maxSize = request.get("maxFileSize");
        if (!isValidSize(maxSize)) return Result.error("格式错误，示例：10MB、100KB");
        String key = "upload.max_file_size.user." + username;
        systemConfigService.upsert("upload.max_file_size.user", key, maxSize, "用户 " + username + " 的上传限制");
        return Result.success("已更新用户上传限制", null);
    }

    @DeleteMapping("/upload-limits/user/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUserUploadLimit(@PathVariable String username) {
        systemConfigService.deleteByKey("upload.max_file_size.user." + username);
        return Result.success("已删除用户上传限制", null);
    }

    // ── 内容大小限制（图片/视频/Excel，支持角色覆盖） ──────────────────────────

    /**
     * 获取所有内容大小限制配置。
     * 返回结构：{ defaults: {image,video,excelImport}, roleOverrides: [{type,role,value,bytes},...],
     *            userOverrides: [{type,username,realName,role,value,bytes},...] }
     */
    @GetMapping("/content-limits")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Map<String, Object>> getContentLimits() {
        List<SystemConfig> all = systemConfigService.listByGroup(GROUP_CONTENT);

        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("image",       "10MB");
        defaults.put("video",       "512MB");
        defaults.put("excelImport", "10MB");

        List<Map<String, Object>> roleOverrides = new ArrayList<>();
        List<Map<String, Object>> userOverrides = new ArrayList<>();

        for (SystemConfig c : all) {
            String key = c.getConfigKey();
            String val = c.getConfigValue();
            if ("upload.content_size.image".equals(key))        defaults.put("image", val);
            else if ("upload.content_size.video".equals(key))   defaults.put("video", val);
            else if ("upload.content_size.excel_import".equals(key)) defaults.put("excelImport", val);
            else if (key.startsWith("upload.content_size.") && key.contains(".role.")) {
                // upload.content_size.{type}.role.{ROLE}
                String suffix = key.substring("upload.content_size.".length());
                int roleIdx = suffix.indexOf(".role.");
                if (roleIdx > 0) {
                    String type = suffix.substring(0, roleIdx);
                    String role = suffix.substring(roleIdx + 6);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", key);
                    item.put("type", type);
                    item.put("role", role);
                    item.put("value", val);
                    item.put("bytes", SizeParseUtil.parseSize(val, 0));
                    roleOverrides.add(item);
                }
            } else if (key.startsWith("upload.content_size.") && key.contains(".user.")) {
                // upload.content_size.{type}.user.{username}
                String suffix = key.substring("upload.content_size.".length());
                int userIdx = suffix.indexOf(".user.");
                if (userIdx > 0) {
                    String type = suffix.substring(0, userIdx);
                    String username = suffix.substring(userIdx + 6);
                    User u = userService.getUserByUsername(username);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", key);
                    item.put("type", type);
                    item.put("username", username);
                    item.put("realName", u != null ? u.getRealName() : null);
                    item.put("role", u != null ? u.getRole() : null);
                    item.put("value", val);
                    item.put("bytes", SizeParseUtil.parseSize(val, 0));
                    userOverrides.add(item);
                }
            }
        }

        // 补充 bytes 字段到 defaults
        Map<String, Object> defaultsWithBytes = new LinkedHashMap<>();
        defaults.forEach((k, v) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", v);
            entry.put("bytes", SizeParseUtil.parseSize(v, 0));
            defaultsWithBytes.put(k, entry);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaults", defaultsWithBytes);
        result.put("roleOverrides", roleOverrides);
        result.put("userOverrides", userOverrides);
        return Result.success(result);
    }

    /**
     * 批量保存内容大小限制配置。
     * 请求体：[{ key: "upload.content_size.image", value: "20MB" }, ...]
     *  key 支持格式：
     *    - upload.content_size.{image|video|excel_import}            全局默认
     *    - upload.content_size.{image|video}.role.{ROLE}             角色覆盖
     *    - upload.content_size.{image|video}.user.{username}         特权用户
     */
    @PutMapping("/content-limits")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Void> updateContentLimits(@RequestBody List<Map<String, String>> items) {
        if (items == null || items.isEmpty()) return Result.error("请求体不能为空");
        for (Map<String, String> item : items) {
            String key = item.get("key");
            String value = item.get("value");
            if (key == null || !key.startsWith("upload.content_size.")) return Result.error("非法 key: " + key);
            if (!isValidSize(value)) return Result.error("格式错误（" + key + "），示例：10MB");
            String description = buildDescription(key);
            systemConfigService.upsert(GROUP_CONTENT, key, value.toUpperCase(), description);
        }
        return Result.success("已保存内容大小限制", null);
    }

    /**
     * 删除角色覆盖或特权用户条目。key 必须包含 ".role." 或 ".user."
     */
    @DeleteMapping("/content-limits/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<Void> deleteContentLimit(@PathVariable String key) {
        if (key == null || !key.startsWith("upload.content_size.")
                || (!key.contains(".role.") && !key.contains(".user."))) {
            return Result.error("只能删除角色覆盖或特权用户条目");
        }
        systemConfigService.deleteByKey(key);
        return Result.success("已删除条目", null);
    }

    /**
     * 搜索用户（用于「特权用户」下拉选择）
     * GET /api/ops/system-config/content-limits/users/search?keyword=xxx&role=STUDENT
     */
    @GetMapping("/content-limits/users/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
    public Result<List<Map<String, Object>>> searchUsersForLimit(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role) {
        List<User> users = userService.searchUsers(keyword, role);
        List<Map<String, Object>> data = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("realName", u.getRealName());
            item.put("role", u.getRole());
            data.add(item);
        }
        return Result.success(data);
    }

    // ── 工具方法 ───────────────────────────────────────────────────────────────

    private boolean isValidSize(String s) {
        return s != null && SIZE_PATTERN.matcher(s.trim()).matches();
    }

    private String buildDescription(String key) {
        String typeLabel = null;
        if (key.contains(".role.")) {
            String suffix = key.substring("upload.content_size.".length());
            int i = suffix.indexOf(".role.");
            String type = suffix.substring(0, i);
            String role = suffix.substring(i + 6);
            typeLabel = ("image".equals(type) ? "图片" : "video".equals(type) ? "视频" : type);
            return typeLabel + "上传限制（" + role + " 角色覆盖）";
        }
        if (key.contains(".user.")) {
            String suffix = key.substring("upload.content_size.".length());
            int i = suffix.indexOf(".user.");
            String type = suffix.substring(0, i);
            String username = suffix.substring(i + 6);
            typeLabel = ("image".equals(type) ? "图片" : "video".equals(type) ? "视频" : type);
            return typeLabel + "上传限制（特权用户 " + username + "）";
        }
        if (key.endsWith("image"))        return "图片上传大小限制（全局默认）";
        if (key.endsWith("video"))        return "视频上传大小限制（全局默认）";
        if (key.endsWith("excel_import")) return "Excel批量导入大小限制";
        return key;
    }
}

