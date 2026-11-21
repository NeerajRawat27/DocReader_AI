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

        rateLimiterService = new RateLimiterService(userRepository, documentRepository, passwordEncoder, jwtTokenProvider);
        ReflectionTestUtils.setField(rateLimiterService, "maxDemoQueries", 2);
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
    void testDemoUserEnforcesRateLimit() {
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
}
