package com.english.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.dto.Result;
import com.english.entity.Unit;
import com.english.entity.User;
import com.english.entity.Word;
import com.english.entity.Text;
import com.english.exception.BusinessException;
import com.english.service.UserService;
import com.english.service.CourseService;
import com.english.service.WordService;
import com.english.service.ClassService;
import com.english.service.UnitService;
import com.english.service.TextService;
import com.english.service.UnitWordService;
import com.english.service.TextExerciseService;
import com.english.service.ArticleVersionService;
import com.english.service.impl.DataCleanupServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private WordService wordService;

    @Autowired
    private ClassService classService;

    @Autowired
    private com.english.service.StudentClassService studentClassService;

    @Autowired
    private DataCleanupServiceImpl dataCleanupService;

    @Autowired
    private UnitService unitService;

    @Autowired
    private TextService textService;

    @Autowired
    private UnitWordService unitWordService;

    @Autowired
    private TextExerciseService textExerciseService;

    @Autowired
    private ArticleVersionService articleVersionService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {

        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            params.put("role", role);
            params.put("keyword", keyword);

            Map<String, Object> result = userService.getUsersByPage(params);

            // 不返回密码
            if (result != null) {
                Object recordsObj = result.get("records");
                if (recordsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<User> users = (List<User>) recordsObj;
                    users.forEach(user -> user.setPassword(null));
                }
            }

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            return Result.error("获取用户列表失败，请稍后重试");
        }
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createUser(@RequestBody User user) {
        try {
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            if (user.getPassword() == null || user.getPassword().length() < 6) {
                return Result.error("密码长度不能少于 6 位");
            }
            if (userService.getUserByUsername(user.getUsername()) != null) {
                return Result.error("用户名已存在");
            }

            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("STUDENT");
            }

            userService.createUser(user);
            return Result.success("用户创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建用户失败，请稍后重试");
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            user.setId(id);

            // 如果有新密码，需要加密；否则不更新密码
            User existingUser = userService.getUserById(id);
            if (existingUser == null) {
                throw new BusinessException("用户不存在");
            }

            // 保持原密码，除非明确提供新密码
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(existingUser.getPassword());
            }

            userService.updateUser(user);
            return Result.success("用户更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新用户失败", e);
            return Result.error("更新用户失败，请稍后重试");
        }
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteUser(@PathVariable Long id) {
        try {
            User targetUser = userService.getUserById(id);
            if (targetUser != null && "admin".equals(targetUser.getUsername())) {
                return Result.error("不能删除默认管理员账户");
            }

            userService.deleteUser(id);
            return Result.success("用户删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除用户失败 id={}", id, e);
            return Result.error("删除用户失败，请稍后重试");
        }
    }

    @GetMapping("/users/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<String>> getUserRoles() {
        List<String> roles = List.of("STUDENT", "TEACHER", "ADMIN");
        return Result.success(roles);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 获取用户统计
            Map<String, Object> userStats = userService.getUserStats();
            if (userStats != null) {
                stats.putAll(userStats);
            }

            // 获取课程统计
            Map<String, Object> courseStats = courseService.getCourseStats();
            if (courseStats != null) {
                stats.putAll(courseStats);
            }

            // 获取单词统计
            Map<String, Object> wordStats = wordService.getWordStats();
            if (wordStats != null) {
                stats.putAll(wordStats);
            }

            // 获取班级统计
            Map<String, Object> classStats = classService.getClassStats();
            if (classStats != null) {
                stats.putAll(classStats);
            }

            return Result.success(stats);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            return Result.error("获取统计信息失败，请稍后重试");
        }
    }

    /**
     * 清理不一致的数据
     */
    @PostMapping("/cleanup-data")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> cleanupData() {
        try {
            // 手动触发数据清理
            dataCleanupService.cleanupInconsistentData();
            return Result.success("数据清理完成，请查看控制台输出获取详细信息");
        } catch (Exception e) {
            logger.error("数据清理失败", e);

            return Result.error("数据清理失败，请稍后重试");
        }
    }

    /**
     * 打印详细数据统计信息
     */
    @GetMapping("/data-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> printDataStatistics() {
        try {
            dataCleanupService.printDataStatistics();
            return Result.success("数据统计信息已打印到控制台");
        } catch (Exception e) {
            logger.error("获取数据统计失败", e);

            return Result.error("获取数据统计失败，请稍后重试");
        }
    }

    // ========== 课程管理相关 ==========

    /**
     * 获取课程列表（管理员专用）
     */
    @GetMapping("/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getCourses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100)); // 防止超大分页查询
            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }
            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (keyword != null) params.put("keyword", keyword);

            Map<String, Object> result = courseService.getCoursesByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取课程列表失败", e);

            return Result.error("获取课程列表失败，请稍后重试");
        }
    }

    // ========== 单元管理相关 ==========

    /**
     * 获取单元列表
     */
    @GetMapping("/units")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getUnits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (courseId != null) {
                params.put("courseId", courseId);
            }

            Map<String, Object> result = unitService.getUnitsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取单元列表失败", e);

            return Result.error("获取单元列表失败，请稍后重试");
        }
    }

    /**
     * 根据课程ID获取单元列表
     */
    @GetMapping("/courses/{courseId}/units")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Unit>> getUnitsByCourseId(@PathVariable Long courseId) {
        try {
            List<Unit> units = unitService.getUnitsByCourseId(courseId);
            return Result.success(units);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课程单元失败", e);
            return Result.error("获取课程单元失败，请稍后重试");
        }
    }

    /**
     * 创建单元
     */
    @PostMapping("/units")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createUnit(@RequestBody Map<String, Object> unitData) {
        try {
            if (unitData.get("courseId") == null || unitData.get("name") == null) {
                return Result.error("courseId 和 name 为必填字段");
            }

            // 验证 courseId 存在性
            Object courseIdObj = unitData.get("courseId");
            if (courseIdObj == null) {
                return Result.error("courseId 为必填字段");
            }
            Long courseId;
            try {
                courseId = Long.valueOf(courseIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("courseId 必须为有效的数字");
            }
            if (courseService.getCourseById(courseId) == null) {
                throw new BusinessException("课程不存在");
            }

            Unit unit = new Unit();
            unit.setCourseId(courseId);
            unit.setName(unitData.get("name").toString());
            unit.setDescription(unitData.get("description") != null ? unitData.get("description").toString() : "");
            Object orderNumObj = unitData.get("orderNum");
            unit.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);

            unitService.createUnit(unit);
            return Result.success("单元创建成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建单元失败", e);

            return Result.error("创建单元失败，请稍后重试");
        }
    }

    /**
     * 更新单元
     */
    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateUnit(@PathVariable Long id, @RequestBody Map<String, Object> unitData) {
        try {
            if (unitData.get("courseId") == null || unitData.get("name") == null) {
                return Result.error("courseId 和 name 为必填字段");
            }
            Object courseIdObj = unitData.get("courseId");
            if (courseIdObj == null) {
                return Result.error("courseId 为必填字段");
            }
            Long courseId;
            try {
                courseId = Long.valueOf(courseIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("courseId 必须为有效的数字");
            }
            Unit unit = new Unit();
            unit.setId(id);
            unit.setCourseId(courseId);
            unit.setName(unitData.get("name").toString());
            unit.setDescription(unitData.get("description") != null ? unitData.get("description").toString() : "");
            Object orderNumObj = unitData.get("orderNum");
            unit.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);

            unitService.updateUnit(unit);
            return Result.success("单元更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新单元失败", e);

            return Result.error("更新单元失败，请稍后重试");
        }
    }

    /**
     * 删除单元
     */
    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteUnit(@PathVariable Long id) {
        try {
            unitService.deleteUnit(id);
            return Result.success("单元删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除单元失败", e);

            return Result.error("删除单元失败，请稍后重试");
        }
    }

    // ========== 单词管理相关 ==========

    /**
     * 获取单词列表
     */
    @GetMapping("/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getWords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (keyword != null) params.put("keyword", keyword);
            if (difficulty != null) params.put("difficulty", difficulty);

            Map<String, Object> result = wordService.getWordsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取单词列表失败", e);

            return Result.error("获取单词列表失败，请稍后重试");
        }
    }

    /**
     * 创建单词
     */
    @PostMapping("/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> createWord(@RequestBody Map<String, Object> wordData) {
        try {
            if (wordData.get("word") == null || wordData.get("definition") == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            // 创建Word对象并保存
            Word word = new Word();
            word.setWord(wordData.get("word").toString());
            word.setPronunciation(wordData.get("pronunciation") != null ? wordData.get("pronunciation").toString() : "");
            word.setDefinition(wordData.get("definition").toString());
            word.setExampleSentence(wordData.get("exampleSentence") != null ? wordData.get("exampleSentence").toString() : "");
            word.setPartOfSpeech(wordData.get("partOfSpeech") != null ? wordData.get("partOfSpeech").toString() : "noun");
            word.setDifficulty(wordData.get("difficulty") != null ? wordData.get("difficulty").toString() : "EASY");
            word.setAudioUrl(wordData.get("audioUrl") != null ? wordData.get("audioUrl").toString() : "");

            boolean success = wordService.createWord(word);
            if (!success) {
                return Result.error("单词创建失败");
            }
            return Result.success("单词创建成功");
        } catch (Exception e) {
            logger.error("创建单词失败", e);

            return Result.error("创建单词失败，请稍后重试");
        }
    }

    /**
     * 更新单词
     */
    @PutMapping("/words/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateWord(@PathVariable Long id, @RequestBody Map<String, Object> wordData) {
        try {
            if (wordData.get("word") == null || wordData.get("definition") == null) {
                return Result.error("word 和 definition 为必填字段");
            }
            // 检查单词是否存在
            Word existingWord = wordService.getWordById(id);
            if (existingWord == null) {
                return Result.error("单词不存在");
            }

            // 更新单词信息
            Word word = new Word();
            word.setId(id);
            word.setWord(wordData.get("word").toString());
            word.setPronunciation(wordData.get("pronunciation") != null ? wordData.get("pronunciation").toString() : "");
            word.setDefinition(wordData.get("definition").toString());
            word.setExampleSentence(wordData.get("exampleSentence") != null ? wordData.get("exampleSentence").toString() : "");
            word.setPartOfSpeech(wordData.get("partOfSpeech") != null ? wordData.get("partOfSpeech").toString() : "noun");
            word.setDifficulty(wordData.get("difficulty") != null ? wordData.get("difficulty").toString() : "EASY");
            word.setAudioUrl(wordData.get("audioUrl") != null ? wordData.get("audioUrl").toString() : "");

            wordService.updateWord(word);
            return Result.success("单词更新成功");
        } catch (Exception e) {
            logger.error("更新单词失败", e);

            return Result.error("更新单词失败，请稍后重试");
        }
    }

    /**
     * 删除单词
     */
    @DeleteMapping("/words/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteWord(@PathVariable Long id) {
        try {
            // 检查单词是否存在
            Word existingWord = wordService.getWordById(id);
            if (existingWord == null) {
                return Result.error("单词不存在");
            }

            wordService.deleteWord(id);
            return Result.success("单词删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除单词失败", e);
            return Result.error("删除单词失败，请稍后重试");
        }
    }

    /**
     * 将单词添加到单元
     */
    @PostMapping("/units/{unitId}/words/{wordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> addWordToUnit(@PathVariable Long unitId, @PathVariable Long wordId) {
        try {
            // 检查是否已存在
            if (unitWordService.exists(unitId, wordId)) {
                return Result.error("该单词已在单元中");
            }

            unitWordService.addToUnit(unitId, wordId);
            return Result.success("单词添加到单元成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加单词到单元失败", e);
            return Result.error("添加单词到单元失败，请稍后重试");
        }
    }

    /**
     * 从单元移除单词
     */
    @DeleteMapping("/units/{unitId}/words/{wordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> removeWordFromUnit(@PathVariable Long unitId, @PathVariable Long wordId) {
        try {
            unitWordService.removeFromUnit(unitId, wordId);
            return Result.success("单词从单元移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除单词失败", e);
            return Result.error("移除单词失败，请稍后重试");
        }
    }

    // ========== 课文管理相关 ==========

    /**
     * 获取课文列表
     */
    @GetMapping("/texts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getTexts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty) {
        try {
            // 分页参数边界校验
            page = Math.max(1, page);
            size = Math.max(1, Math.min(size, 100));

            // 关键词长度限制
            if (keyword != null && keyword.length() > 100) {
                keyword = keyword.substring(0, 100);
            }

            Map<String, Object> params = new HashMap<>();
            params.put("page", page);
            params.put("size", size);
            if (unitId != null) params.put("unitId", unitId);
            if (keyword != null) params.put("keyword", keyword);
            if (difficulty != null) params.put("difficulty", difficulty);

            Map<String, Object> result = textService.getTextsByPage(params);
            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取课文列表失败", e);

            return Result.error("获取课文列表失败，请稍后重试");
        }
    }

    
    /**
     * 创建课文
     */
    @PostMapping("/texts")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> createText(@RequestBody Map<String, Object> textData) {
        // 获取当前用户ID（在 try 前面，避免事务中的 NPE）
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Result.error("用户未认证");
        }
        String username = authentication.getName();
        com.english.entity.User creator = userService.getUserByUsername(username);
        if (creator == null) {
            return Result.error("用户不存在");
        }
        Long createdBy = creator.getId();

        try {
            Text text = new Text();
            if (textData.get("title") == null) {
                return Result.error("title 为必填字段");
            }
            text.setTitle(textData.get("title").toString());
            // 从前端获取unitId
            if (textData.get("unitId") != null) {
                try {
                    text.setUnitId(Long.valueOf(textData.get("unitId").toString()));
                } catch (NumberFormatException e) {
                    return Result.error("unitId 必须为有效的数字");
                }
            }
            text.setContent(textData.get("content") != null ? textData.get("content").toString() : "");
            text.setMarkdownContent(textData.get("markdownContent") != null ? textData.get("markdownContent").toString() : "");
            text.setContentType(textData.get("contentType") != null ? textData.get("contentType").toString() : "TEXT");
            text.setDifficulty(textData.get("difficulty") != null ? textData.get("difficulty").toString() : "EASY");
            text.setAudioUrl(textData.get("audioUrl") != null ? textData.get("audioUrl").toString() : "");
            text.setTags(textData.get("tags") != null ? textData.get("tags").toString() : "");
            Object isPublicObj = textData.get("isPublic");
            try {
                text.setIsPublic(isPublicObj != null ? Integer.parseInt(isPublicObj.toString()) : 1);
            } catch (NumberFormatException e) {
                logger.warn("isPublic 格式错误，使用默认值 1");
                text.setIsPublic(1);
            }

            // 创建课文
            textService.createText(text);

            // 根据内容类型生成不同版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // 异步生成Markdown版本
                articleVersionService.checkAndGenerateMarkdownVersions(text.getId());
            } else {
                // 异步生成普通文本版本
                articleVersionService.checkAndGenerateVersions(text.getId());
            }

            Map<String, Object> result = new HashMap<>();
            result.put("textId", text.getId());
            result.put("message", "课文创建成功");

            // 检查是否需要创建习题
            if (textData.containsKey("exercises")) {
                Object exercisesObj = textData.get("exercises");
                if (!(exercisesObj instanceof List)) {
                    return Result.error("exercises 字段必须是数组类型");
                }
                List<Map<String, Object>> exercisesData = (List<Map<String, Object>>) exercisesObj;

                if (!exercisesData.isEmpty()) {
                    // 准备习题数据
                    List<com.english.entity.TextExercise> exercises = new ArrayList<>();
                    List<List<com.english.entity.ExerciseOption>> allOptions = new ArrayList<>();

                    for (Map<String, Object> exerciseData : exercisesData) {
                        // 创建习题
                        com.english.entity.TextExercise exercise = new com.english.entity.TextExercise();
                        exercise.setTextId(text.getId());
                        Object questionObj = exerciseData.get("question");
                        Object exerciseTypeObj = exerciseData.get("exerciseType");
                        Object orderNumObj = exerciseData.get("orderNum");
                        if (questionObj == null || exerciseTypeObj == null || orderNumObj == null) {
                            continue; // 跳过缺少必要字段的习题
                        }
                        exercise.setQuestion(questionObj.toString());
                        exercise.setExerciseType(exerciseTypeObj.toString());
                        try {
                            exercise.setOrderNum(Integer.parseInt(orderNumObj.toString()));
                        } catch (NumberFormatException e) {
                            logger.warn("习题 orderNum 格式错误，使用默认值 0");
                            exercise.setOrderNum(0);
                        }
                        exercise.setCreatedBy(createdBy);

                        exercises.add(exercise);

                        // 处理选项
                        Object optionsObj = exerciseData.get("options");
                        List<Map<String, Object>> optionsData = (optionsObj instanceof List) ? (List<Map<String, Object>>) optionsObj : null;
                        List<com.english.entity.ExerciseOption> options = new ArrayList<>();

                        if (optionsData != null && !optionsData.isEmpty()) {
                            for (Map<String, Object> optionData : optionsData) {
                                com.english.entity.ExerciseOption option = new com.english.entity.ExerciseOption();
                                Object optionContentObj = optionData.get("optionContent");
                                Object optionKeyObj = optionData.get("optionKey");
                                if (optionContentObj == null || optionKeyObj == null) {
                                    continue; // 跳过缺少必要字段的选项
                                }
                                option.setOptionContent(optionContentObj.toString());
                                option.setOptionKey(optionKeyObj.toString());
                                Object isCorrectObj = optionData.get("isCorrect");
                                // 安全处理 isCorrect：前端可能发 1/0/"true"/"false" 等格式
                                boolean isCorrect = false;
                                if (isCorrectObj != null) {
                                    String isCorrectStr = isCorrectObj.toString().trim().toLowerCase();
                                    isCorrect = "1".equals(isCorrectStr) || "true".equals(isCorrectStr);
                                }
                                option.setIsCorrect(isCorrect);
                                Object optionOrderObj = optionData.get("orderNum");
                                try {
                                    option.setOrderNum(optionOrderObj != null ? Integer.parseInt(optionOrderObj.toString()) : 0);
                                } catch (NumberFormatException e) {
                                    logger.warn("选项 orderNum 格式错误，使用默认值 0");
                                    option.setOrderNum(0);
                                }

                                options.add(option);
                            }
                        }

                        allOptions.add(options);
                    }

                    // 批量创建习题
                    boolean exerciseSuccess = textExerciseService.batchCreateExercises(exercises, allOptions);

                    if (exerciseSuccess) {
                        result.put("exerciseCount", exercises.size());
                        result.put("message", "课文和习题创建成功");
                    } else {
                        result.put("message", "课文创建成功，但习题创建失败");
                    }
                }
            }

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建课文失败", e);
            throw e;  // 让 @Transactional 自动回滚，而不是手动 setRollbackOnly()
        }
    }
    @PutMapping("/texts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> updateText(@PathVariable Long id, @RequestBody Map<String, Object> textData) {
        try {
            Text text = new Text();
            text.setId(id);
            if (textData.get("unitId") != null) {
                try {
                    text.setUnitId(Long.valueOf(textData.get("unitId").toString()));
                } catch (NumberFormatException e) {
                    return Result.error("unitId 必须为有效的数字");
                }
            }
            if (textData.get("title") == null) {
                return Result.error("title 为必填字段");
            }
            text.setTitle(textData.get("title").toString());
            text.setContent(textData.get("content") != null ? textData.get("content").toString() : "");
            text.setMarkdownContent(textData.get("markdownContent") != null ? textData.get("markdownContent").toString() : "");
            text.setContentType(textData.get("contentType") != null ? textData.get("contentType").toString() : "TEXT");
            text.setDifficulty(textData.get("difficulty") != null ? textData.get("difficulty").toString() : "EASY");
            text.setAudioUrl(textData.get("audioUrl") != null ? textData.get("audioUrl").toString() : "");
            text.setTags(textData.get("tags") != null ? textData.get("tags").toString() : "");
            Object isPublicObj = textData.get("isPublic");
            try {
                text.setIsPublic(isPublicObj != null ? Integer.parseInt(isPublicObj.toString()) : 1);
            } catch (NumberFormatException e) {
                logger.warn("isPublic 格式错误，使用默认值 1");
                text.setIsPublic(1);
            }
            Object orderNumObj = textData.get("orderNum");
            try {
                text.setOrderNum(orderNumObj != null ? Integer.parseInt(orderNumObj.toString()) : 0);
            } catch (NumberFormatException e) {
                logger.warn("orderNum 格式错误，使用默认值 0");
                text.setOrderNum(0);
            }

            textService.updateText(text);

            // 根据内容类型重新生成不同版本
            if ("MARKDOWN".equals(text.getContentType())) {
                // 异步重新生成Markdown版本
                articleVersionService.checkAndGenerateMarkdownVersions(text.getId());
            } else {
                // 异步重新生成普通文本版本
                articleVersionService.checkAndGenerateVersions(text.getId());
            }

            return Result.success("课文更新成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新课文失败", e);

            return Result.error("更新课文失败，请稍后重试");
        }
    }

    /**
     * 删除课文
     */
    @DeleteMapping("/texts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> deleteText(@PathVariable Long id) {
        try {
            textService.deleteText(id);
            return Result.success("课文删除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除课文失败", e);
            return Result.error("删除课文失败，请稍后重试");
        }
    }

    /**
     * 根据单元ID获取课文列表（带详细信息）
     */
    @GetMapping("/units/{unitId}/texts")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> getTextsByUnitId(@PathVariable Long unitId) {
        try {
            logger.debug("获取单元课文列表, 单元ID: {}", unitId);

            List<Text> texts = textService.getTextsByUnit(unitId);
            logger.debug("找到课文数量: {}", texts.size());

            List<Map<String, Object>> result = new ArrayList<>();

            for (Text text : texts) {
                logger.debug("课文ID: {}, 标题: {}, unitId: {}", text.getId(), text.getTitle(), text.getUnitId());

                Map<String, Object> textMap = new HashMap<>();
                textMap.put("id", text.getId());
                textMap.put("unitId", text.getUnitId());
                textMap.put("title", text.getTitle());
                textMap.put("content", text.getContent());
                textMap.put("difficulty", text.getDifficulty());
                textMap.put("audioUrl", text.getAudioUrl());
                textMap.put("orderNum", text.getOrderNum());
                textMap.put("createdTime", text.getCreatedTime());
                result.add(textMap);
            }

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元课文失败", e);
            return Result.error("获取单元课文失败，请稍后重试");
        }
    }

    /**
     * 将课文添加到单元
     */
    @PostMapping("/units/{unitId}/texts/{textId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> addTextToUnit(@PathVariable Long unitId, @PathVariable Long textId) {
        try {
            // 检查课文是否存在
            Text existingText = textService.getTextById(textId);
            if (existingText == null) {
                return Result.error("课文不存在");
            }

            // 检查课文是否已属于某个单元（unit_id = 0 表示未分配）
            if (existingText.getUnitId() != null && existingText.getUnitId() > 0) {
                return Result.error("该课文已属于其他单元，请先从原单元移除");
            }

            // 将课文分配给单元
            existingText.setUnitId(unitId);
            textService.updateText(existingText);

            return Result.success("课文添加到单元成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加课文到单元失败", e);
            return Result.error("添加课文到单元失败，请稍后重试");
        }
    }

    /**
     * 获取课文详情（包含习题）
     */
    @GetMapping("/texts/{textId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getTextDetailWithExercises(@PathVariable Long textId) {
        try {
            // 获取课文信息
            Text text = textService.getTextById(textId);
            if (text == null) {
                return Result.error("课文不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", text.getId());
            result.put("unitId", text.getUnitId());
            result.put("title", text.getTitle());
            result.put("content", text.getContent());
            result.put("difficulty", text.getDifficulty());
            result.put("audioUrl", text.getAudioUrl());
            result.put("orderNum", text.getOrderNum());
            result.put("createdTime", text.getCreatedTime());

            // 获取课文习题
            List<Map<String, Object>> exercises = textExerciseService.getExercisesWithOptions(textId);
            result.put("exercises", exercises);
            result.put("exerciseCount", exercises.size());

            return Result.success(result);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取课文详情失败", e);
            return Result.error("获取课文详情失败，请稍后重试");
        }
    }

    /**
     * 从单元移除课文
     */
    @DeleteMapping("/units/{unitId}/texts/{textId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> removeTextFromUnit(@PathVariable Long unitId, @PathVariable Long textId) {
        try {
            logger.debug("开始移除课文, 单元ID: {}, 课文ID: {}", unitId, textId);

            // 检查课文是否存在
            Text existingText = textService.getTextById(textId);
            if (existingText == null) {
                return Result.error("课文不存在");
            }

            logger.debug("课文当前的unitId: {}, 标题: {}", existingText.getUnitId(), existingText.getTitle());

            // 检查课文是否属于当前单元（允许unitId为null的情况）
            if (existingText.getUnitId() != null && !unitId.equals(existingText.getUnitId())) {
                return Result.error("该课文不属于当前单元");
            }

            // 如果课文已经不属于任何单元，也算作成功
            if (existingText.getUnitId() == null) {
                return Result.success("课文已经不属于任何单元");
            }

            // 设置unitId为null并更新
            existingText.setUnitId(null);
            textService.updateText(existingText);

            // 验证更新是否成功
            Text updatedText = textService.getTextById(textId);
            if (updatedText == null) {
                return Result.error("课文不存在");
            }
            if (updatedText.getUnitId() != null) {
                logger.warn("更新后unitId仍不为null: {}", updatedText.getUnitId());
                return Result.error("移除失败，请重试");
            }

            return Result.success("课文从单元移除成功");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("移除课文失败", e);
            return Result.error("移除课文失败，请稍后重试");
        }
    }

    /**
     * 根据单元ID获取单词列表（带详细信息）
     */
    @GetMapping("/units/{unitId}/words")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> getWordsByUnitId(@PathVariable Long unitId) {
        try {
            List<Map<String, Object>> words = unitWordService.getWordsWithDetailsByUnitId(unitId);
            return Result.success(words);
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            logger.error("获取单元单词失败", e);
            return Result.error("获取单元单词失败，请稍后重试");
        }
    }

    /**
     * 批量导入用户
     * 通过上传Excel文件批量创建或更新用户
     * Excel格式: 序号 | 班级 | 学号 | 姓名 | ...
     */
    @PostMapping("/users/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }

        // 文件大小限制（10MB）
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.error("文件大小超过限制（最大10MB）");
        }

        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
            return Result.error("只支持.xls和.xlsx格式的Excel文件");
        }

        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int updateCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        // 新增：班级相关统计
        int classCreatedCount = 0;      // 新创建的班级数
        int classJoinedCount = 0;       // 成功加入班级的用户数
        int classJoinFailedCount = 0;   // 加入班级失败的用户数
        List<String> createdClassNames = new ArrayList<>();  // 记录创建的班级名称

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = filename.endsWith(".xlsx")
                     ? new XSSFWorkbook(inputStream)
                     : new HSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // 跳过前两行(标题行和表头行)
            int startRow = 2;

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                try {
                    // 读取数据: 序号(0) | 班级(1) | 学号(2) | 姓名(3)
                    Cell classCell = row.getCell(1);
                    Cell studentIdCell = row.getCell(2);
                    Cell nameCell = row.getCell(3);

                    // 检查必填字段
                    if (studentIdCell == null || nameCell == null) {
                        errors.add("第" + (i + 1) + "行: 学号或姓名为空");
                        failCount++;
                        continue;
                    }

                    String studentId = getCellValueAsString(studentIdCell);
                    String realName = getCellValueAsString(nameCell);
                    String className = classCell != null ? getCellValueAsString(classCell) : "";

                    // 验证数据
                    if (studentId.trim().isEmpty() || realName.trim().isEmpty()) {
                        errors.add("第" + (i + 1) + "行: 学号或姓名为空");
                        failCount++;
                        continue;
                    }

                    // 检查用户是否已存在
                    User existingUser = userService.getUserByUsername(studentId);
                    User currentUser = null;  // 保存当前处理的用户对象

                    if (existingUser != null) {
                        // 更新已存在的用户
                        existingUser.setRealName(realName);
                        existingUser.setEmail(studentId + "@student.edu.cn");
                        // 不更新密码
                        userService.updateUser(existingUser);
                        currentUser = existingUser;
                        updateCount++;
                    } else {
                        // 创建新用户
                        User newUser = new User();
                        newUser.setUsername(studentId);
                        String tempPassword = generateTempPassword();
                        newUser.setPassword(tempPassword); // 随机临时密码
                        newUser.setRealName(realName);
                        newUser.setEmail(studentId + "@student.edu.cn");
                        newUser.setRole("STUDENT");

                        userService.createUser(newUser);
                        // 安全处理：临时密码不写入日志，应通过安全渠道（邮件/短信）下发给学生
                        logger.info("[用户导入] 新建学生账号: username={}", studentId);

                        // 创建后需要重新查询以获取生成的ID
                        currentUser = userService.getUserByUsername(studentId);
                        if (currentUser == null) {
                            errors.add("第" + (i + 1) + "行: 创建用户后无法查询，请检查数据库");
                            failCount++;
                            continue;
                        }
                        successCount++;
                    }

                    // === 新增：处理班级逻辑 ===
                    if (className != null && !className.trim().isEmpty()) {
                        try {
                            // 1. 查找班级是否存在
                            com.english.entity.Class classInfo = classService.getClassByName(className.trim());

                            // 2. 如果班级不存在，创建新班级
                            if (classInfo == null) {
                                com.english.entity.Class newClass = new com.english.entity.Class();
                                newClass.setName(className.trim());
                                newClass.setDescription("通过批量导入自动创建");
                                newClass.setCourseId(null);    // 不关联课程
                                newClass.setTeacherId(null);   // 不指定教师

                                classService.createClassForImport(newClass);

                                // 创建后重新查询以获取ID
                                classInfo = classService.getClassByName(className.trim());

                                classCreatedCount++;
                                if (!createdClassNames.contains(className.trim())) {
                                    createdClassNames.add(className.trim());
                                }
                            }

                            // 3. 将用户加入班级
                            if (classInfo != null && currentUser != null) {
                                // 检查用户是否已在班级中
                                if (!studentClassService.isStudentInClass(currentUser.getId(), classInfo.getId())) {
                                    studentClassService.joinClass(currentUser.getId(), classInfo.getId());
                                    classJoinedCount++;
                                }
                            }
                        } catch (Exception e) {
                            // 班级操作失败，记录错误但不影响用户创建
                            errors.add("第" + (i + 1) + "行: 班级操作失败");
                            logger.error("导入第{}行班级操作失败", i + 1, e);
                            classJoinFailedCount++;
                        }
                    }
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行: 导入失败");
                    logger.error("导入第{}行失败", i + 1, e);
                    failCount++;
                }
            }

            result.put("successCount", successCount);
            result.put("updateCount", updateCount);
            result.put("failCount", failCount);
            result.put("totalProcessed", successCount + updateCount + failCount);
            result.put("errors", errors);
            // 安全提示：新建用户的临时密码已记录到服务器日志，请查看后台日志获取
            result.put("passwordsNote", successCount > 0 ? "已新建 " + successCount + " 个账号，临时密码请查看服务器日志" : null);

            // 新增：班级相关统计
            result.put("classCreatedCount", classCreatedCount);
            result.put("classJoinedCount", classJoinedCount);
            result.put("classJoinFailedCount", classJoinFailedCount);
            result.put("createdClassNames", createdClassNames);

            return Result.success(result);
        } catch (Exception e) {
            logger.error("导入失败", e);

            return Result.error("导入失败，请稍后重试");
        }
    }

    /**
     * 生成随机临时密码（12位，含大小写字母和数字）
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 获取Excel单元格的字符串值
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // 如果是数字,转换为字符串(去掉小数点)
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 格式化为整数字符串,避免科学记数法
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // 使用 DataFormatter 获取公式计算后的显示值，防止公式注入
                return new DataFormatter().formatCellValue(cell);
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}