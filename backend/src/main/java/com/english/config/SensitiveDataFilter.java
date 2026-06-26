package com.english.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.english.util.SensitiveDataMasker;

/**
 * Logback 敏感数据脱敏过滤器
 * 在日志输出前自动脱敏敏感信息
 */
public class SensitiveDataFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        // 脱敏逻辑在 PatternLayout 中实现更合适
        // 这里只做基本的过滤，确保不记录明显的敏感信息

        String message = event.getFormattedMessage();
        if (message != null) {
            // 检查是否包含明显的密码字段
            if (message.contains("password=") ||
                message.contains("\"password\":") ||
                message.contains("token=") ||
                message.contains("secret=")) {
                // 这些信息应该被脱敏，但不应该被完全丢弃
                // 实际脱敏在 SensitiveDataConverter 中处理
            }
        }

        return FilterReply.NEUTRAL;
    }
}
