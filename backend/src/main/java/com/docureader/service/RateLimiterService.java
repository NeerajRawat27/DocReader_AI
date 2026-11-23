package com.docureader.service;

import com.docureader.dto.DemoSessionResponse;
import com.docureader.exception.RateLimitExceededException;
import com.docureader.model.User;
import com.docureader.repository.DocumentRepository;
import com.docureader.repository.UserRepository;
import com.docureader.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    @Value("${docureader.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${docureader.demo.max-queries:5}")
    private int maxDemoQueries;

    @Value("${docureader.demo.window-minutes:60}")
    private int demoWindowMinutes;

    @Value("${docureader.demo.max-sessions-per-ip:10}")
    private int maxSessionsPerIp;

    // In-memory fallback: Track usage per userId: remaining quota
    private final Map<Long, Integer> demoQueryUsage = new ConcurrentHashMap<>();

    // In-memory fallback: Track demo session creation timestamps per client IP (sliding 1-hour window)
    private final Map<String, java.util.List<Long>> ipSessionCreations = new ConcurrentHashMap<>();

    public RateLimiterService(UserRepository userRepository,
                              DocumentRepository documentRepository,
                              PasswordEncoder passwordEncoder,
                              JwtTokenProvider tokenProvider,
                              @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTemplate = redisTemplate;
    }

    public boolean isRedisActive() {
        return redisEnabled && redisTemplate != null;
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

        // Track in Redis with native TTL, or fallback to in-memory
        if (isRedisActive()) {
            try {
                String usageKey = "demo:usage:" + demoUser.getId();
                String sessionKey = "demo:session:" + demoUser.getId();
                redisTemplate.opsForValue().set(usageKey, "0", demoWindowMinutes, TimeUnit.MINUTES);
                redisTemplate.opsForValue().set(sessionKey, username, demoWindowMinutes, TimeUnit.MINUTES);
                log.info("Initialized Redis demo session and usage for user {} with TTL {}m", demoUser.getId(), demoWindowMinutes);
            } catch (Exception e) {
                log.warn("Redis session registration failed ({}), falling back to in-memory store", e.getMessage());
                demoQueryUsage.put(demoUser.getId(), 0);
            }
        } else {
            demoQueryUsage.put(demoUser.getId(), 0);
        }

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

    public void enforceIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            clientIp = "unknown";
        }

        if (isRedisActive()) {
            try {
                String ipKey = "demo:ip:" + clientIp;
                Long count = redisTemplate.opsForValue().increment(ipKey);
                if (count != null && count == 1) {
                    redisTemplate.expire(ipKey, 1, TimeUnit.HOURS);
                }
                if (count != null && count > maxSessionsPerIp) {
                    log.warn("Redis IP rate limit exceeded for IP: {} ({} creations in 1 hour)", clientIp, count);
                    throw new RateLimitExceededException(
                            "Demo session limit reached for your IP address (" + maxSessionsPerIp + " per hour). " +
                                    "Please wait or sign up for a permanent account."
                    );
                }
                return;
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis IP rate limit check failed ({}), falling back to in-memory store", e.getMessage());
            }
        }

        enforceIpRateLimitInMemory(clientIp);
    }

    private synchronized void enforceIpRateLimitInMemory(String clientIp) {
        long now = System.currentTimeMillis();
        long windowStart = now - (60L * 60L * 1000L); // 1 hour sliding window

        java.util.List<Long> timestamps = ipSessionCreations.computeIfAbsent(clientIp, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        timestamps.removeIf(ts -> ts < windowStart);

        if (timestamps.size() >= maxSessionsPerIp) {
            log.warn("In-memory rate limit exceeded for IP: {} ({} creations in 1 hour)", clientIp, timestamps.size());
            throw new RateLimitExceededException(
                    "Demo session limit reached for your IP address (" + maxSessionsPerIp + " per hour). " +
                            "Please wait or sign up for a permanent account."
                    );
        }

        timestamps.add(now);
    }

    public void recordAndCheckRateLimit(User user) {
        if (!user.isDemo()) {
            return; // Registered users have standard access
        }

        if (isRedisActive()) {
            try {
                String key = "demo:usage:" + user.getId();
                String currentVal = redisTemplate.opsForValue().get(key);
                int currentCount = currentVal != null ? Integer.parseInt(currentVal) : 0;

                if (currentCount >= maxDemoQueries) {
                    throw new RateLimitExceededException(
                            "Demo quota exceeded. You have reached the maximum limit of " + maxDemoQueries +
                                    " free queries. Please register or sign in for unlimited access."
                    );
                }

                Long newCount = redisTemplate.opsForValue().increment(key);
                if (newCount != null && newCount == 1) {
                    redisTemplate.expire(key, demoWindowMinutes, TimeUnit.MINUTES);
                }
                log.info("Redis demo user {} query count: {}/{}", user.getUsername(), newCount, maxDemoQueries);
                return;
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis rate limit check failed ({}), falling back to in-memory store", e.getMessage());
            }
        }

        recordAndCheckRateLimitInMemory(user);
    }

    private synchronized void recordAndCheckRateLimitInMemory(User user) {
        int currentCount = demoQueryUsage.getOrDefault(user.getId(), 0);
        if (currentCount >= maxDemoQueries) {
            throw new RateLimitExceededException(
                    "Demo quota exceeded. You have reached the maximum limit of " + maxDemoQueries +
                            " free queries. Please register or sign in for unlimited access."
            );
        }

        demoQueryUsage.put(user.getId(), currentCount + 1);
        log.info("In-memory demo user {} query count: {}/{}", user.getUsername(), currentCount + 1, maxDemoQueries);
    }

    public int getRemainingQueries(User user) {
        if (!user.isDemo()) {
            return Integer.MAX_VALUE;
        }

        if (isRedisActive()) {
            try {
                String val = redisTemplate.opsForValue().get("demo:usage:" + user.getId());
                int used = (val != null) ? Integer.parseInt(val) : 0;
                return Math.max(0, maxDemoQueries - used);
            } catch (Exception e) {
                log.warn("Redis getRemainingQueries failed ({}), falling back to in-memory store", e.getMessage());
            }
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
                if (isRedisActive()) {
                    try {
                        redisTemplate.delete("demo:usage:" + user.getId());
                        redisTemplate.delete("demo:session:" + user.getId());
                    } catch (Exception e) {
                        log.warn("Failed to delete Redis keys for demo user {}: {}", user.getId(), e.getMessage());
                    }
                }
                demoQueryUsage.remove(user.getId());
                userRepository.delete(user);
            }
        }

        // Clean up in-memory IP rate limit tracking older than 1 hour
        long windowStart = System.currentTimeMillis() - (60L * 60L * 1000L);
        ipSessionCreations.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(ts -> ts < windowStart);
            return entry.getValue().isEmpty();
        });
    }
}
