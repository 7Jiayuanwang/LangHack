package com.english.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary download token service for large file downloads.
 * Frontend exchanges JWT auth for a short-lived token, then uses it
 * in a direct browser download URL — avoiding the blob/memory approach.
 */
@Service
public class DownloadTokenService {

    private static final Logger logger = LoggerFactory.getLogger(DownloadTokenService.class);

    private static final long TOKEN_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private final ConcurrentHashMap<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    /**
     * Generate a one-time download token for a backup file.
     */
    public String generateToken(Long backupId, String username) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(backupId, username, System.currentTimeMillis() + TOKEN_TTL_MS));
        logger.info("Download token generated: backupId={}, user={}", backupId, username);
        return token;
    }

    /**
     * Validate and consume a download token. Returns the backup ID if valid, null otherwise.
     * Token is consumed on successful validation (one-time use).
     */
    public TokenInfo validateToken(String token) {
        TokenInfo info = tokens.get(token);
        if (info == null) {
            return null;
        }
        if (info.expiresAt < System.currentTimeMillis()) {
            tokens.remove(token);
            return null;
        }
        tokens.remove(token); // one-time use
        return info;
    }

    /**
     * Periodic cleanup of expired tokens (runs every 5 minutes).
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = tokens.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().expiresAt < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.debug("Cleaned up {} expired download tokens", removed);
        }
    }

    public static class TokenInfo {
        public final Long backupId;
        public final String username;
        public final long expiresAt;

        TokenInfo(Long backupId, String username, long expiresAt) {
            this.backupId = backupId;
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
