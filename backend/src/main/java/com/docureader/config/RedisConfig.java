package com.docureader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.time.Duration;

/**
 * Redis configuration for caching and TTL-based rate limiting.
 * Active only when docureader.redis.enabled=true.
 */
@Configuration
@ConditionalOnProperty(name = "docureader.redis.enabled", havingValue = "true")
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${REDIS_URL:#{null}}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration timeout;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout != null ? timeout : Duration.ofSeconds(2))
                .build();

        RedisStandaloneConfiguration redisConfig;
        if (redisUrl != null && !redisUrl.trim().isEmpty()) {
            try {
                log.info("Configuring Redis connection using REDIS_URL");
                URI uri = URI.create(redisUrl.trim());
                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 6379;
                redisConfig = new RedisStandaloneConfiguration(host, port);
                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    if (userInfo.length == 2) {
                        redisConfig.setUsername(userInfo[0]);
                        redisConfig.setPassword(RedisPassword.of(userInfo[1]));
                    } else if (userInfo.length == 1) {
                        redisConfig.setPassword(RedisPassword.of(userInfo[0]));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse REDIS_URL, falling back to host/port: {}", e.getMessage());
                redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
                if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                    redisConfig.setPassword(RedisPassword.of(redisPassword));
                }
            }
        } else {
            log.info("Configuring Redis connection using host {}:{}", redisHost, redisPort);
            redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                redisConfig.setPassword(RedisPassword.of(redisPassword));
            }
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring RedisTemplate with Jackson serializer");
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
