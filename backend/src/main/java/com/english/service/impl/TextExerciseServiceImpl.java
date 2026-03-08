package com.english.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;
import com.english.exception.BusinessException;
import com.english.mapper.TextExerciseMapper;
import com.english.mapper.ExerciseOptionMapper;
import com.english.service.TextExerciseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课文习题服务实现类
 */
@Service
public class TextExerciseServiceImpl extends ServiceImpl<TextExerciseMapper, TextExercise> implements TextExerciseService {

    private static final Logger logger = LoggerFactory.getLogger(TextExerciseServiceImpl.class);

    @Autowired
    private TextExerciseMapper textExerciseMapper;

    @Autowired
    private ExerciseOptionMapper exerciseOptionMapper;

    @Override
    @Transactional
    public boolean createTextExercise(TextExercise textExercise) {
        int result = textExerciseMapper.insert(textExercise);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean batchCreateExercises(List<TextExercise> exercises, List<List<ExerciseOption>> options) {
        if (exercises == null || exercises.isEmpty()) {
            return false;
        }

        // 前置校验：先验证再执行，避免不必要的数据库操作
        if (options == null || options.size() != exercises.size()) {
            throw new BusinessException("习题列表和选项列表数量不匹配");
        }

        // 插入习题，获取ID
        for (TextExercise exercise : exercises) {
            int result = textExerciseMapper.insert(exercise);
            if (result <= 0) {
                throw new BusinessException("插入习题失败");
            }
            if (exercise.getId() == null) {
                throw new BusinessException("习题主键未回填，请检查实体 @TableId 配置");
            }
        }
        for (int i = 0; i < exercises.size(); i++) {
            TextExercise exercise = exercises.get(i);
            List<ExerciseOption> exerciseOptions = options.get(i);

            if (exerciseOptions != null && !exerciseOptions.isEmpty()) {
                for (ExerciseOption option : exerciseOptions) {
                    option.setExerciseId(exercise.getId());
                    int result = exerciseOptionMapper.insert(option);
                    if (result <= 0) {
                        throw new BusinessException("插入选项失败");
                    }
                }
            }
        }

        return true;
    }

    @Override
    public List<Map<String, Object>> getExercisesWithOptions(Long textId) {
        try {
            // 获取课文的所有习题
            List<TextExercise> exercises = textExerciseMapper.getExercisesByTextId(textId);

            List<Map<String, Object>> result = new ArrayList<>();

            if (exercises == null) {
                return result;
            }

            for (TextExercise exercise : exercises) {
                Map<String, Object> exerciseMap = new HashMap<>();
                exerciseMap.put("id", exercise.getId());
                exerciseMap.put("question", exercise.getQuestion());
                exerciseMap.put("exerciseType", exercise.getExerciseType());
                exerciseMap.put("orderNum", exercise.getOrderNum());

                // 获取习题的选项
                List<ExerciseOption> options = exerciseOptionMapper.getOptionsByExerciseId(exercise.getId());
                exerciseMap.put("options", options);

                result.add(exerciseMap);
            }

            return result;
        } catch (Exception e) {
            logger.error("获取课文习题失败", e);
            throw new BusinessException("获取习题失败，请稍后重试");
        }
    }

    @Override
    public int getExerciseCount(Long textId) {
        try {
            return textExerciseMapper.countExercisesByTextId(textId);
        } catch (Exception e) {
            logger.error("获取习题数量失败", e);
            throw new BusinessException("获取习题数量失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public boolean updateExercise(TextExercise textExercise) {
        try {
            int result = textExerciseMapper.updateById(textExercise);
            return result > 0;
        } catch (Exception e) {
            logger.error("更新习题失败", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteExercise(Long exerciseId) {
        // 先删除选项
        exerciseOptionMapper.deleteOptionsByExerciseId(exerciseId);
        // 再删除习题
        int result = textExerciseMapper.deleteById(exerciseId);
        return result > 0;
    }

    @Override
    @Transactional
    public boolean deleteAllExercisesByTextId(Long textId) {
        // 获取课文的所有习题
        List<TextExercise> exercises = textExerciseMapper.getExercisesByTextId(textId);

        if (exercises == null) {
            return true;
        }

        for (TextExercise exercise : exercises) {
            // 先删除选项
            exerciseOptionMapper.deleteOptionsByExerciseId(exercise.getId());
            // 再删除习题
            textExerciseMapper.deleteById(exercise.getId());
        }

        return true;
    }

    @Override
    public Map<String, Object> getExerciseDetail(Long exerciseId) {
        try {
            TextExercise exercise = textExerciseMapper.selectById(exerciseId);
            if (exercise == null) {
                return null;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", exercise.getId());
            result.put("question", exercise.getQuestion());
            result.put("exerciseType", exercise.getExerciseType());
            result.put("orderNum", exercise.getOrderNum());

            // 获取选项
            List<ExerciseOption> options = exerciseOptionMapper.getOptionsByExerciseId(exerciseId);
            result.put("options", options);

            return result;
        } catch (Exception e) {
            logger.error("获取习题详情失败", e);
            throw new BusinessException("获取习题详情失败，请稍后重试");
        }
    }

    @Override
    public boolean checkAnswer(Long exerciseId, String studentAnswer) {
        try {
            ExerciseOption correctOption = exerciseOptionMapper.getCorrectOption(exerciseId);
            if (correctOption == null) {
                return false;
            }

            return correctOption.getOptionKey() != null &&
                    correctOption.getOptionKey().equalsIgnoreCase(studentAnswer);
        } catch (Exception e) {
            logger.error("检查答案失败", e);
            throw new BusinessException("检查答案失败，请稍后重试");
        }
    }

    @Override
    public List<Long> getExerciseIdsByTextId(Long textId) {
        try {
            // 方法1：使用已有的 getExercisesByTextId 方法，然后提取ID
            List<TextExercise> exercises = textExerciseMapper.getExercisesByTextId(textId);

            if (exercises == null || exercises.isEmpty()) {
                return new ArrayList<>();
            }

            return exercises.stream()
                    .map(TextExercise::getId)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("获取课文习题ID列表失败", e);
            throw new BusinessException("获取习题ID列表失败，请稍后重试");
        }
    }
}