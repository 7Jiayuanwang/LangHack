package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.LearnerAgentProfile;
import com.english.entity.LevelExamScore;
import com.english.entity.User;
import com.english.mapper.LearnerAgentProfileMapper;
import com.english.mapper.LevelExamScoreMapper;
import com.english.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** 425 分是产品学习层级切换线；历史数据里的 is_passed 不可靠。 */
@Service
@RequiredArgsConstructor
public class ArticleInsightLevelService {
    private static final BigDecimal NEXT_LEVEL_SCORE = new BigDecimal("425");

    private final UserService userService;
    private final LevelExamScoreMapper scoreMapper;
    private final LearnerAgentProfileMapper profileMapper;

    public Decision resolve(Long userId) {
        User user = userService.getUserById(userId);
        boolean hasCet4 = false;
        boolean hasCet6 = false;
        boolean cet4Reached = false;
        boolean cet6Reached = false;
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            List<LevelExamScore> scores = scoreMapper.selectList(new QueryWrapper<LevelExamScore>()
                    .eq("student_id", user.getUsername())
                    .select("exam_type", "exam_name", "score"));
            for (LevelExamScore score : scores) {
                int level = examLevel(score);
                if (level == 4) {
                    hasCet4 = true;
                    cet4Reached |= reached(score);
                } else if (level == 6) {
                    hasCet6 = true;
                    cet6Reached |= reached(score);
                }
            }
        }
        if (cet6Reached) return new Decision("POSTGRADUATE", "六级成绩已达到 425 分，试试考研层级的表达");
        if (cet4Reached) return new Decision("CET6", "四级成绩已达到 425 分，向六级知识点进阶");
        if (hasCet6) return new Decision("CET6", "已有六级考试记录，继续巩固六级知识点");
        if (hasCet4) return new Decision("CET4", "依据现有四级成绩，先巩固四级知识点");

        LearnerAgentProfile profile = profileMapper.selectOne(new QueryWrapper<LearnerAgentProfile>()
                .eq("user_id", userId).last("LIMIT 1"));
        String selfLevel = profile == null ? null : profile.getCurrentLevel();
        if ("ADVANCED".equalsIgnoreCase(selfLevel)) {
            return new Decision("POSTGRADUATE", "暂未查到四、六级成绩，按你自评的高级水平推荐");
        }
        if ("UPPER_INTERMEDIATE".equalsIgnoreCase(selfLevel)) {
            return new Decision("CET6", "暂未查到四、六级成绩，按你自评的中高级水平推荐");
        }
        return new Decision("CET4", "暂未查到四、六级成绩，从四级知识点开始；可在学习档案调整水平");
    }

    private boolean reached(LevelExamScore score) {
        return score.getScore() != null && score.getScore().compareTo(NEXT_LEVEL_SCORE) >= 0;
    }

    private int examLevel(LevelExamScore score) {
        String name = score.getExamName() == null ? "" : score.getExamName().toUpperCase(Locale.ROOT)
                .replace("-", "").replace(" ", "");
        if (name.contains("口语") || name.contains("CETSET")) return 0;
        if (name.contains("CET6") || name.contains("英语六级") || name.contains("英语6级")) return 6;
        if (name.contains("CET4") || name.contains("英语四级") || name.contains("英语4级")) return 4;
        String type = score.getExamType() == null ? "" : score.getExamType().toUpperCase(Locale.ROOT)
                .replace("-", "").replace(" ", "");
        if ("CET6".equals(type)) return 6;
        if ("CET4".equals(type)) return 4;
        return 0;
    }

    public record Decision(String level, String reason) { }
}
