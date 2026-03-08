package com.english.service;

import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;

import java.util.List;
import java.util.Map;

/**
 * 课文习题服务接口
 */
public interface TextExerciseService {

    /**
     * 创建课文习题
     * @param textExercise 习题对象
     * @return 创建结果
     */
    boolean createTextExercise(TextExercise textExercise);

    /**
     * 批量创建课文习题
     * @param exercises 习题列表
     * @param options 选项列表（每道题对应一个选项列表）
     * @return 创建结果
     */
    boolean batchCreateExercises(List<TextExercise> exercises, List<List<ExerciseOption>> options);

    /**
     * 获取课文的所有习题
     * @param textId 课文ID
     * @return 习题列表（包含选项）
     */
    List<Map<String, Object>> getExercisesWithOptions(Long textId);

    /**
     * 获取课文习题数量
     * @param textId 课文ID
     * @return 习题数量
     */
    int getExerciseCount(Long textId);

    /**
     * 更新习题
     * @param textExercise 习题对象
     * @return 更新结果
     */
    boolean updateExercise(TextExercise textExercise);

    /**
     * 删除习题
     * @param exerciseId 习题ID
     * @return 删除结果
     */
    boolean deleteExercise(Long exerciseId);

    /**
     * 删除课文的所有习题
     * @param textId 课文ID
     * @return 删除结果
     */
    boolean deleteAllExercisesByTextId(Long textId);

    /**
     * 获取习题详情（包含选项）
     * @param exerciseId 习题ID
     * @return 习题详情
     */
    Map<String, Object> getExerciseDetail(Long exerciseId);

    /**
     * 检查学生答案
     * @param exerciseId 习题ID
     * @param studentAnswer 学生答案
     * @return 是否正确
     */
    boolean checkAnswer(Long exerciseId, String studentAnswer);

    List<Long> getExerciseIdsByTextId(Long textId);
}