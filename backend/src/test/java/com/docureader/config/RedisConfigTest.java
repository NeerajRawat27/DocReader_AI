package com.docureader.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RedisConfigTest {

    @Test
    void testRedisConfigBeans() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "redisHost", "127.0.0.1");
        ReflectionTestUtils.setField(config, "redisPort", 6379);
        ReflectionTestUtils.setField(config, "timeout", Duration.ofSeconds(2));

        RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);

        StringRedisTemplate stringRedisTemplate = config.stringRedisTemplate(connectionFactory);
        assertNotNull(stringRedisTemplate);
        assertEquals(connectionFactory, stringRedisTemplate.getConnectionFactory());

        RedisTemplate<String, Object> redisTemplate = config.redisTemplate(connectionFactory);
        assertNotNull(redisTemplate);
        assertEquals(connectionFactory, redisTemplate.getConnectionFactory());
    }

    @Test
    void testRedisConnectionFactoryWithRedisUrl() {
        RedisConfig config = new RedisConfig();
        ReflectionTestUtils.setField(config, "redisUrl", "redis://default:secret123@redis-node:6380");
        ReflectionTestUtils.setField(config, "timeout", Duration.ofSeconds(3));

        RedisConnectionFactory factory = config.redisConnectionFactory();
        assertNotNull(factory);
    }
}
