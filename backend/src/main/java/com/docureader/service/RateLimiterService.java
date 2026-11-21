package com.docureader.service;

import com.docureader.dto.DemoSessionResponse;
import com.docureader.exception.RateLimitExceededException;
import com.docureader.model.User;
import com.docureader.repository.DocumentRepository;
import com.docureader.repository.UserRepository;
import com.docureader.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${docureader.demo.max-queries:5}")
    private int maxDemoQueries;

    @Value("${docureader.demo.window-minutes:60}")
    private int demoWindowMinutes;

    @Value("${docureader.demo.max-sessions-per-ip:10}")
    private int maxSessionsPerIp;

    // Track usage per userId: remaining quota
    private final Map<Long, Integer> demoQueryUsage = new ConcurrentHashMap<>();

    // Track demo session creation timestamps per client IP (sliding 1-hour window)
    private final Map<String, java.util.List<Long>> ipSessionCreations = new ConcurrentHashMap<>();

    public RateLimiterService(UserRepository userRepository,
                              DocumentRepository documentRepository,
                              PasswordEncoder passwordEncoder,
                              JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public DemoSessionResponse createDemoSession() {
        return createDemoSession("unknown");
    }

    @Transactional
    public DemoSessionResponse createDemoSession(String clientIp) {
        enforceIpRateLimit(clientIp);

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "guest_" + randomSuffix;
        String email = "guest_" + randomSuffix + "@demo.docureader.ai";
        String rawPassword = UUID.randomUUID().toString();

        User demoUser = new User(username, email, passwordEncoder.encode(rawPassword), true);
        demoUser = userRepository.save(demoUser);

        demoQueryUsage.put(demoUser.getId(), 0);

        String token = tokenProvider.generateDemoToken(demoUser.getId(), username);
        long expiresAt = System.currentTimeMillis() + (demoWindowMinutes * 60L * 1000L);

        return new DemoSessionResponse(
                token,
                demoUser.getId(),
                demoUser.getUsername(),
                maxDemoQueries,
                maxDemoQueries,
                expiresAt
        );
    }

    private synchronized void enforceIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            clientIp = "unknown";
        }
        long now = System.currentTimeMillis();
        long windowStart = now - (60L * 60L * 1000L); // 1 hour sliding window

        java.util.List<Long> timestamps = ipSessionCreations.computeIfAbsent(clientIp, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        // Remove older entries outside window
        timestamps.removeIf(ts -> ts < windowStart);

        if (timestamps.size() >= maxSessionsPerIp) {
            log.warn("Rate limit exceeded for IP: {} ({} creations in 1 hour)", clientIp, timestamps.size());
            throw new RateLimitExceededException(
                    "Demo session limit reached for your IP address (" + maxSessionsPerIp + " per hour). " +
                            "Please wait or sign up for a permanent account."
            );
        }

        timestamps.add(now);
    }

    public synchronized void recordAndCheckRateLimit(User user) {
        if (!user.isDemo()) {
            return; // Registered users have standard access
        }

        int currentCount = demoQueryUsage.getOrDefault(user.getId(), 0);
        if (currentCount >= maxDemoQueries) {
            throw new RateLimitExceededException(
                    "Demo quota exceeded. You have reached the maximum limit of " + maxDemoQueries +
                            " free queries. Please register or sign in for unlimited access."
            );
        }

        demoQueryUsage.put(user.getId(), currentCount + 1);
        log.info("Demo user {} query count: {}/{}", user.getUsername(), currentCount + 1, maxDemoQueries);
    }

    public int getRemainingQueries(User user) {
        if (!user.isDemo()) {
            return Integer.MAX_VALUE;
        }
        int used = demoQueryUsage.getOrDefault(user.getId(), 0);
        return Math.max(0, maxDemoQueries - used);
    }

    public int getMaxDemoQueries() {
        return maxDemoQueries;
    }

    /**
     * Short-lived job cleanup: Runs every 15 minutes to delete demo users and their documents older than 2 hours.
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void cleanupOldDemoSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        var oldDemoUsers = userRepository.findByIsDemoTrueAndCreatedAtBefore(threshold);
        if (!oldDemoUsers.isEmpty()) {
            log.info("Cleaning up {} expired demo guest accounts", oldDemoUsers.size());
            for (User user : oldDemoUsers) {
                demoQueryUsage.remove(user.getId());
                userRepository.delete(user);
            }
        }

        // Clean up IP rate limit tracking older than 1 hour
        long windowStart = System.currentTimeMillis() - (60L * 60L * 1000L);
        ipSessionCreations.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(ts -> ts < windowStart);
            return entry.getValue().isEmpty();
        });
    }
}
