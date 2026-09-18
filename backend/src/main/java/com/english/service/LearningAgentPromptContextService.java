package com.english.service;

/**
 * 为学习伙伴对话构建最小必要、可安全发送给模型的用户学习上下文。
 */
public interface LearningAgentPromptContextService {

    /**
     * 读取用户画像并转换为提示词上下文。读取失败时返回空字符串，避免影响基础问答。
     */
    String buildForUser(Long userId);
}
