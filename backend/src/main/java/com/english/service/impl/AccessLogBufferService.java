package com.english.service.impl;

import com.english.entity.AccessLog;
import com.english.mapper.AccessLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class AccessLogBufferService {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogBufferService.class);
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int BATCH_SIZE = 500;

    private final ConcurrentLinkedQueue<AccessLog> buffer = new ConcurrentLinkedQueue<>();
    private final AccessLogMapper accessLogMapper;

    public AccessLogBufferService(AccessLogMapper accessLogMapper) {
        this.accessLogMapper = accessLogMapper;
    }

    public void add(AccessLog log) {
        if (buffer.size() >= MAX_QUEUE_SIZE) {
            buffer.poll(); // Drop oldest to prevent OOM
        }
        buffer.offer(log);
    }

    @Scheduled(fixedRate = 10000)
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        List<AccessLog> batch = new ArrayList<>();
        AccessLog item;
        while ((item = buffer.poll()) != null && batch.size() < BATCH_SIZE) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                accessLogMapper.batchInsert(batch);
                logger.debug("Flushed {} access logs to DB", batch.size());
            } catch (Exception e) {
                logger.warn("Failed to flush access logs, {} records lost", batch.size(), e);
            }
        }
    }
}
