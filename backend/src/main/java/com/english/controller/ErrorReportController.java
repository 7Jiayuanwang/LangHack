package com.english.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accepts frontend beacon error reports so the client does not hit a 404.
 */
@RestController
@RequestMapping("/api/errors")
public class ErrorReportController {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportController.class);
    private static final int MAX_PAYLOAD_LENGTH = 4000;

    @PostMapping(
            value = "/report",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        String sanitizedPayload = sanitizePayload(payload);
        String sanitizedUserAgent = sanitizeHeader(userAgent);

        if (sanitizedPayload.isEmpty()) {
            log.warn("Frontend error report received with empty payload. userAgent={}", sanitizedUserAgent);
            return;
        }

        log.warn("Frontend error report received. userAgent={}, payload={}", sanitizedUserAgent, sanitizedPayload);
    }

    private String sanitizePayload(String payload) {
        if (payload == null) {
            return "";
        }

        String normalized = payload
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();

        if (normalized.length() <= MAX_PAYLOAD_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)";
    }

    private String sanitizeHeader(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return "unknown";
        }

        return headerValue
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }
}
