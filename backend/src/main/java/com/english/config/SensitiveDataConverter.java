package com.english.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.english.util.SensitiveDataMasker;

/**
 * Logback 消息脱敏转换器
 * 在 logback-spring.xml 中配置使用
 */
public class SensitiveDataConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String originalMessage = super.convert(event);
        return SensitiveDataMasker.maskSensitiveData(originalMessage);
    }
}
