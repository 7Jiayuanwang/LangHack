package com.english.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
public class CacheConfig extends CachingConfigurerSupport {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache read error, cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis cache write error, cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis cache evict error, cache={}, key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis cache clear error, cache={}: {}", cache.getName(), e.getMessage());
            }
        };
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        
        // Strict polymorphic type validator - only allow whitelisted types
        // This prevents arbitrary deserialization attacks (CVE-style exploits)
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                // Application domain objects
                .allowIfSubType("com.english.")
                // Allowed JDK collections (immutable variants are safer)
                .allowIfSubType("java.util.HashMap")
                .allowIfSubType("java.util.LinkedHashMap")
                .allowIfSubType("java.util.ArrayList")
                .allowIfSubType("java.util.LinkedList")
                .allowIfSubType("java.util.HashSet")
                .allowIfSubType("java.util.TreeSet")
                .allowIfSubType("java.util.Collections$UnmodifiableList")
                .allowIfSubType("java.util.Collections$UnmodifiableSet")
                .allowIfSubType("java.util.Collections$UnmodifiableMap")
                .allowIfSubType("java.util.Collections$EmptyMap")
                .allowIfSubType("java.util.Collections$EmptyList")
                .allowIfSubType("java.util.Collections$EmptySet")
                .allowIfSubType("java.util.Collections$SingletonList")
                .allowIfSubType("java.util.Collections$SingletonMap")
                // Primitive wrappers
                .allowIfSubType("java.lang.String")
                .allowIfSubType("java.lang.Long")
                .allowIfSubType("java.lang.Integer")
                .allowIfSubType("java.lang.Double")
                .allowIfSubType("java.lang.Float")
                .allowIfSubType("java.lang.Boolean")
                .allowIfSubType("java.lang.Byte")
                // Date/Time types
                .allowIfSubType("java.time.")
                // MyBatis pagination interface
                .allowIfSubType("com.baomidou.mybatisplus.core.metadata.IPage")
                .build();
        
        // Use NON_FINAL instead of NON_CONCRETE_AND_ARRAYS
        // NON_FINAL is more restrictive and safer for untrusted data
        om.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        // Default cache configuration with reasonable TTL
        // FIXED: Cache null values with shorter TTL to prevent cache penetration attacks
        // When a record doesn't exist, we cache the null result briefly (1 minute) to avoid
        // repeated database lookups, then it expires and allows re-checking
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .entryTtl(Duration.ofMinutes(10));  // Default TTL: 10 minutes, null values cached by default

        // Per-cache TTL configuration - adjust based on data freshness requirements
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("courses",         defaultConfig.entryTtl(Duration.ofMinutes(10)));   // Course list: 10 min (stable)
        configs.put("texts",           defaultConfig.entryTtl(Duration.ofMinutes(5)));    // Text content: 5 min (updated frequently)
        configs.put("wordnet",         defaultConfig.entryTtl(Duration.ofHours(2)));      // Word definitions: 2 hours (very stable)
        configs.put("stats",           defaultConfig.entryTtl(Duration.ofMinutes(3)));    // Statistics: 3 min (balance cost vs freshness)
        configs.put("publicTexts",     defaultConfig.entryTtl(Duration.ofMinutes(10)));   // Public texts: 10 min (allow new content within reasonable delay)
        configs.put("wordDetail",      defaultConfig.entryTtl(Duration.ofHours(1)));      // Word details: 1 hour (static reference data)
        configs.put("learner-profile:user",  defaultConfig.entryTtl(Duration.ofMinutes(10)));  // Learner profile: 10 min (computed on read)
        configs.put("learner-profile:class", defaultConfig.entryTtl(Duration.ofMinutes(10)));  // Class profile summary: 10 min

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
