package com.english.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 严格的类型验证器：只允许明确列出的类型，防止反序列化攻击
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.english.")
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.LinkedList")
                .allowIfSubType("java.util.Collections$UnmodifiableList")
                .allowIfSubType("java.lang.String")
                .allowIfSubType("java.lang.Long")
                .allowIfSubType("java.lang.Integer")
                .allowIfSubType("java.lang.Double")
                .allowIfSubType("java.lang.Boolean")
                .allowIfSubType("java.time.")
                .allowIfSubType("com.baomidou.mybatisplus.core.metadata.IPage")
                .build();
        // 使用 NON_FINAL 而非 NON_CONCRETE_AND_ARRAYS：更安全，只序列化非final字段
        om.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("courses",         defaultConfig.entryTtl(Duration.ofMinutes(10)));
        configs.put("texts",           defaultConfig.entryTtl(Duration.ofMinutes(5)));
        configs.put("wordnet",         defaultConfig.entryTtl(Duration.ofHours(2)));
        configs.put("stats",           defaultConfig.entryTtl(Duration.ofMinutes(1)));
        configs.put("publicTexts",     defaultConfig.entryTtl(Duration.ofMinutes(5)));
        configs.put("wordDetail",      defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
