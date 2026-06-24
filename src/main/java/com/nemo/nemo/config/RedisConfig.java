package com.nemo.nemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;

@Configuration
public class RedisConfig {

    private final RedisSerializer<String> prefixingKeySerializer;

    public RedisConfig(AppProperties appProperties) {
        String prefix = appProperties.getRedis().getKeyPrefix();
        this.prefixingKeySerializer = (prefix == null || prefix.isEmpty())
                ? new StringRedisSerializer()
                : new PrefixingStringRedisSerializer(prefix);
    }

    // 일반 RedisTemplate — 키 직렬화에 prefix를 자동 적용
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(prefixingKeySerializer);
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(prefixingKeySerializer);
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    // 문자열 전용 RedisTemplate — 키 직렬화에 prefix를 자동 적용
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.setKeySerializer(prefixingKeySerializer);
        template.setHashKeySerializer(prefixingKeySerializer);
        return template;
    }

    // Redis Pub/Sub 리스너 컨테이너 빈
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

    /**
     * Wraps {@link StringRedisSerializer} to automatically prepend a configured prefix
     * when writing keys to Redis and strip it when reading. Lets application code use
     * logical keys ("album:member:abc") while Redis stores them as "{prefix}album:member:abc".
     *
     * Note: only applied to RedisTemplate KeySerializer / HashKeySerializer. Pub/Sub channel
     * names go through a separate path and must include the prefix explicitly.
     */
    static final class PrefixingStringRedisSerializer implements RedisSerializer<String> {
        private final byte[] prefixBytes;
        private final String prefix;

        PrefixingStringRedisSerializer(String prefix) {
            this.prefix = prefix;
            this.prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] serialize(String key) {
            if (key == null) return null;
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[prefixBytes.length + keyBytes.length];
            System.arraycopy(prefixBytes, 0, out, 0, prefixBytes.length);
            System.arraycopy(keyBytes, 0, out, prefixBytes.length, keyBytes.length);
            return out;
        }

        @Override
        public String deserialize(byte[] bytes) {
            if (bytes == null) return null;
            String s = new String(bytes, StandardCharsets.UTF_8);
            return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
        }
    }
}
