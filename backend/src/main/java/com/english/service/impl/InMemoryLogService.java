package com.english.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory ring buffer that captures recent log entries via a Logback appender.
 * Designed for Docker environments where log file access may be inconvenient.
 */
@Service
public class InMemoryLogService {

    private static final int MAX_ENTRIES = 2000;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final ConcurrentLinkedDeque<Map<String, Object>> buffer = new ConcurrentLinkedDeque<>();

    @PostConstruct
    public void init() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        RingBufferAppender appender = new RingBufferAppender();
        appender.setContext(context);
        appender.setName("IN_MEMORY_RING");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    public static class LogQueryResult {
        public final List<Map<String, Object>> entries;
        public final Map<String, Long> levelCounts;
        LogQueryResult(List<Map<String, Object>> entries, Map<String, Long> levelCounts) {
            this.entries = entries;
            this.levelCounts = levelCounts;
        }
    }

    /**
     * Single-pass query: returns filtered entries (newest first) and full-buffer
     * level counts in one iteration, avoiding two separate traversals.
     *
     * @param limit   max entries to return (capped at MAX_ENTRIES)
     * @param level   filter by level (INFO/WARN/ERROR), null for all
     * @param keyword filter by message/logger keyword, null for all
     */
    public LogQueryResult query(int limit, String level, String keyword) {
        if (limit <= 0 || limit > MAX_ENTRIES) limit = 200;

        // Snapshot the deque once to avoid concurrent modification during iteration
        Object[] snapshot = buffer.toArray();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ERROR", 0L);
        counts.put("WARN", 0L);
        counts.put("INFO", 0L);
        counts.put("DEBUG", 0L);

        List<Map<String, Object>> result = new ArrayList<>();
        String levelUpper = (level != null && !level.isEmpty()) ? level.toUpperCase() : null;
        String kwLower = (keyword != null && !keyword.isEmpty()) ? keyword.toLowerCase() : null;

        // Traverse newest-first (reverse of insertion order)
        for (int i = snapshot.length - 1; i >= 0; i--) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entry = (Map<String, Object>) snapshot[i];
            String lvl = (String) entry.get("level");
            counts.merge(lvl, 1L, Long::sum);

            if (result.size() < limit) {
                if (levelUpper != null && !levelUpper.equalsIgnoreCase(lvl)) continue;
                if (kwLower != null) {
                    String msg = (String) entry.get("message");
                    String logger = (String) entry.get("logger");
                    if ((msg == null || !msg.toLowerCase().contains(kwLower))
                            && (logger == null || !logger.toLowerCase().contains(kwLower))) {
                        continue;
                    }
                }
                result.add(entry);
            }
        }
        return new LogQueryResult(result, counts);
    }

    /** @deprecated Use {@link #query} instead to avoid double traversal. */
    public List<Map<String, Object>> getRecentLogs(int limit, String level, String keyword) {
        return query(limit, level, keyword).entries;
    }

    /** @deprecated Use {@link #query} instead to avoid double traversal. */
    public Map<String, Long> getLevelCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ERROR", 0L);
        counts.put("WARN", 0L);
        counts.put("INFO", 0L);
        counts.put("DEBUG", 0L);
        for (Map<String, Object> entry : buffer) {
            String lvl = (String) entry.get("level");
            counts.merge(lvl, 1L, Long::sum);
        }
        return counts;
    }

    private class RingBufferAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            // Only capture INFO and above for the ops dashboard
            if (event.getLevel().isGreaterOrEqual(Level.INFO)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("timestamp", TIME_FMT.format(Instant.ofEpochMilli(event.getTimeStamp())));
                entry.put("level", event.getLevel().toString());
                entry.put("logger", shortenLogger(event.getLoggerName()));
                entry.put("message", event.getFormattedMessage());
                if (event.getThrowableProxy() != null) {
                    entry.put("exception", event.getThrowableProxy().getClassName()
                            + ": " + event.getThrowableProxy().getMessage());
                }
                entry.put("thread", event.getThreadName());

                buffer.addLast(entry);
                while (buffer.size() > MAX_ENTRIES) {
                    buffer.pollFirst();
                }
            }
        }
    }

    private static String shortenLogger(String name) {
        if (name == null) return "";
        // com.english.service.impl.FooService -> c.e.s.i.FooService
        String[] parts = name.split("\\.");
        if (parts.length <= 2) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i].charAt(0)).append('.');
        }
        sb.append(parts[parts.length - 1]);
        return sb.toString();
    }
}
