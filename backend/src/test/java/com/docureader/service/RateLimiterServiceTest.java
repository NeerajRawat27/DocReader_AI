package com.docureader.service;

import com.docureader.exception.RateLimitExceededException;
import com.docureader.model.User;
import com.docureader.repository.DocumentRepository;
import com.docureader.repository.UserRepository;
import com.docureader.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;
    private UserRepository userRepository;
    private DocumentRepository documentRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        documentRepository = Mockito.mock(DocumentRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);

        // Test in-memory mode by default (redisTemplate = null, redisEnabled = false)
        rateLimiterService = new RateLimiterService(userRepository, documentRepository, passwordEncoder, jwtTokenProvider, null);
        ReflectionTestUtils.setField(rateLimiterService, "maxDemoQueries", 2);
        ReflectionTestUtils.setField(rateLimiterService, "maxSessionsPerIp", 2);
        ReflectionTestUtils.setField(rateLimiterService, "demoWindowMinutes", 60);
    }

    @Test
    void testRegisteredUserNotRateLimited() {
        User regUser = new User("alice", "alice@example.com", "pass", false);
        regUser.setId(10L);

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                rateLimiterService.recordAndCheckRateLimit(regUser);
            }
        });
    }

    @Test
    void testDemoUserEnforcesRateLimitInMemory() {
        User demoUser = new User("guest_123", "guest@example.com", "pass", true);
        demoUser.setId(20L);

        // 1st query passes
        rateLimiterService.recordAndCheckRateLimit(demoUser);
        assertEquals(1, rateLimiterService.getRemainingQueries(demoUser));

        // 2nd query passes
        rateLimiterService.recordAndCheckRateLimit(demoUser);
        assertEquals(0, rateLimiterService.getRemainingQueries(demoUser));

        // 3rd query throws RateLimitExceededException
        assertThrows(RateLimitExceededException.class, () -> {
            rateLimiterService.recordAndCheckRateLimit(demoUser);
        });
    }

    @Test
    void testRedisRateLimitingWithTtl() {
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RateLimiterService redisRateLimiter = new RateLimiterService(
                userRepository, documentRepository, passwordEncoder, jwtTokenProvider, redisTemplate
        );
        ReflectionTestUtils.setField(redisRateLimiter, "redisEnabled", true);
        ReflectionTestUtils.setField(redisRateLimiter, "maxDemoQueries", 2);
        ReflectionTestUtils.setField(redisRateLimiter, "demoWindowMinutes", 60);
        ReflectionTestUtils.setField(redisRateLimiter, "maxSessionsPerIp", 2);

        assertTrue(redisRateLimiter.isRedisActive());

        User demoUser = new User("guest_redis", "guest_redis@demo.ai", "pass", true);
        demoUser.setId(99L);
        String usageKey = "demo:usage:99";

        // 1st query: current count null -> 0, increment returns 1, sets TTL
        Mockito.when(valueOps.get(usageKey)).thenReturn(null);
        Mockito.when(valueOps.increment(usageKey)).thenReturn(1L);

        redisRateLimiter.recordAndCheckRateLimit(demoUser);
        Mockito.verify(valueOps).increment(usageKey);
        Mockito.verify(redisTemplate).expire(Mockito.eq(usageKey), Mockito.eq(60L), Mockito.eq(java.util.concurrent.TimeUnit.MINUTES));

        // 2nd query: current count "1", increment returns 2
        Mockito.when(valueOps.get(usageKey)).thenReturn("1");
        Mockito.when(valueOps.increment(usageKey)).thenReturn(2L);
        redisRateLimiter.recordAndCheckRateLimit(demoUser);

        // 3rd query: current count "2" (max limit is 2) -> throws exception
        Mockito.when(valueOps.get(usageKey)).thenReturn("2");
        assertThrows(RateLimitExceededException.class, () -> {
            redisRateLimiter.recordAndCheckRateLimit(demoUser);
        });

        // Remaining queries
        Mockito.when(valueOps.get(usageKey)).thenReturn("1");
        assertEquals(1, redisRateLimiter.getRemainingQueries(demoUser));
    }

    @Test
    void testRedisIpRateLimitingWithTtl() {
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RateLimiterService redisRateLimiter = new RateLimiterService(
                userRepository, documentRepository, passwordEncoder, jwtTokenProvider, redisTemplate
        );
        ReflectionTestUtils.setField(redisRateLimiter, "redisEnabled", true);
        ReflectionTestUtils.setField(redisRateLimiter, "maxSessionsPerIp", 2);

        String ip = "192.168.1.100";
        String ipKey = "demo:ip:" + ip;

        // 1st session: count 1, sets TTL 1 hour
        Mockito.when(valueOps.increment(ipKey)).thenReturn(1L);
        redisRateLimiter.enforceIpRateLimit(ip);
        Mockito.verify(redisTemplate).expire(Mockito.eq(ipKey), Mockito.eq(1L), Mockito.eq(java.util.concurrent.TimeUnit.HOURS));

        // 2nd session: count 2, under or equal to max
        Mockito.when(valueOps.increment(ipKey)).thenReturn(2L);
        assertDoesNotThrow(() -> redisRateLimiter.enforceIpRateLimit(ip));

        // 3rd session: count 3 > maxSessionsPerIp (2) -> throws exception
        Mockito.when(valueOps.increment(ipKey)).thenReturn(3L);
        assertThrows(RateLimitExceededException.class, () -> redisRateLimiter.enforceIpRateLimit(ip));
    }

    @Test
    void testRedisExceptionGracefulFallbackToInMemory() {
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        Mockito.when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        RateLimiterService resilientService = new RateLimiterService(
                userRepository, documentRepository, passwordEncoder, jwtTokenProvider, redisTemplate
        );
        ReflectionTestUtils.setField(resilientService, "redisEnabled", true);
        ReflectionTestUtils.setField(resilientService, "maxDemoQueries", 2);

        User demoUser = new User("guest_fallback", "guest_fallback@demo.ai", "pass", true);
        demoUser.setId(101L);

        // Redis throws, but method catches and gracefully processes in memory
        assertDoesNotThrow(() -> resilientService.recordAndCheckRateLimit(demoUser));
        assertEquals(1, resilientService.getRemainingQueries(demoUser));
    }
}
