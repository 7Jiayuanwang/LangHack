package com.english.service.impl;

import com.english.dto.learner.LearnerTagDto;
import com.english.entity.LearnerCompositeTag;
import com.english.mapper.LearnerCompositeTagMapper;
import com.english.service.CompositeTagEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CompositeTagEngineImpl implements CompositeTagEngine {

    private static final Logger log = LoggerFactory.getLogger(CompositeTagEngineImpl.class);

    @Autowired
    private LearnerCompositeTagMapper compositeTagMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<LearnerTagDto> evaluateCompositeTags(Set<String> baseTagCodes) {
        List<LearnerTagDto> result = new ArrayList<>();

        List<LearnerCompositeTag> definitions = compositeTagMapper.selectAllEnabled();
        if (definitions == null || definitions.isEmpty()) {
            return result;
        }

        for (LearnerCompositeTag def : definitions) {
            try {
                if (matchesRule(def.getRuleDefinition(), baseTagCodes, null)) {
                    result.add(new LearnerTagDto(
                            def.getCode(),
                            def.getDisplayName(),
                            def.getTagType(),
                            def.getPriority(),
                            def.getActionHint()
                    ));
                }
            } catch (Exception e) {
                log.warn("[CompositeTag] 规则评估失败: code={}, error={}", def.getCode(), e.getMessage());
            }
        }

        return result;
    }

    @Override
    public List<LearnerTagDto> evaluateAll(Map<String, Double> metrics) {
        List<LearnerCompositeTag> definitions = compositeTagMapper.selectAllEnabled();
        if (definitions == null || definitions.isEmpty()) {
            return new ArrayList<>();
        }

        // Pass 1: evaluate tags that only use metric conditions (base/trend tags)
        Set<String> matchedCodes = new HashSet<>();
        List<LearnerTagDto> result = new ArrayList<>();

        List<LearnerCompositeTag> metricOnly = new ArrayList<>();
        List<LearnerCompositeTag> hasTagCondition = new ArrayList<>();

        for (LearnerCompositeTag def : definitions) {
            if (usesTagConditions(def.getRuleDefinition())) {
                hasTagCondition.add(def);
            } else {
                metricOnly.add(def);
            }
        }

        for (LearnerCompositeTag def : metricOnly) {
            try {
                if (matchesRule(def.getRuleDefinition(), matchedCodes, metrics)) {
                    matchedCodes.add(def.getCode());
                    result.add(new LearnerTagDto(
                            def.getCode(), def.getDisplayName(),
                            def.getTagType(), def.getPriority(), def.getActionHint()
                    ));
                }
            } catch (Exception e) {
                log.warn("[CompositeTag] Pass1 规则评估失败: code={}, error={}", def.getCode(), e.getMessage());
            }
        }

        // Pass 2: evaluate composite tags that reference other tags
        for (LearnerCompositeTag def : hasTagCondition) {
            try {
                if (matchesRule(def.getRuleDefinition(), matchedCodes, metrics)) {
                    matchedCodes.add(def.getCode());
                    result.add(new LearnerTagDto(
                            def.getCode(), def.getDisplayName(),
                            def.getTagType(), def.getPriority(), def.getActionHint()
                    ));
                }
            } catch (Exception e) {
                log.warn("[CompositeTag] Pass2 规则评估失败: code={}, error={}", def.getCode(), e.getMessage());
            }
        }

        // Deduplicate by code, keeping higher priority (lower number)
        return result.stream()
                .collect(Collectors.toMap(
                        LearnerTagDto::getCode,
                        t -> t,
                        (a, b) -> a.getPriority() <= b.getPriority() ? a : b
                ))
                .values().stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .collect(Collectors.toList());
    }

    private boolean usesTagConditions(String ruleJson) {
        try {
            JsonNode root = objectMapper.readTree(ruleJson);
            JsonNode conditions = root.get("conditions");
            if (conditions == null || !conditions.isArray()) return false;
            for (JsonNode c : conditions) {
                if ("tag".equals(c.path("source").asText())) return true;
            }
        } catch (Exception e) {
            // parse error, treat as non-tag
        }
        return false;
    }

    private boolean matchesRule(String ruleJson, Set<String> tagCodes, Map<String, Double> metrics) throws Exception {
        JsonNode root = objectMapper.readTree(ruleJson);
        JsonNode conditions = root.get("conditions");
        String combiner = root.has("combiner") ? root.get("combiner").asText() : "AND";

        if (conditions == null || !conditions.isArray()) {
            return false;
        }

        if ("AND".equals(combiner)) {
            for (JsonNode condition : conditions) {
                if (!evaluateCondition(condition, tagCodes, metrics)) {
                    return false;
                }
            }
            return true;
        } else {
            for (JsonNode condition : conditions) {
                if (evaluateCondition(condition, tagCodes, metrics)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean evaluateCondition(JsonNode condition, Set<String> tagCodes, Map<String, Double> metrics) {
        String source = condition.path("source").asText();

        if ("tag".equals(source)) {
            String code = condition.path("code").asText();
            String operator = condition.has("operator") ? condition.get("operator").asText() : "MATCHED";
            boolean matched = tagCodes.contains(code);
            return "MATCHED".equals(operator) ? matched : !matched;
        }

        if ("metric".equals(source)) {
            if (metrics == null) return false;
            String field = condition.path("field").asText();
            String operator = condition.path("operator").asText();
            double threshold = condition.path("value").asDouble();

            Double actual = metrics.get(field);
            if (actual == null) return false;

            switch (operator) {
                case "LT":  return actual < threshold;
                case "LTE": return actual <= threshold;
                case "GT":  return actual > threshold;
                case "GTE": return actual >= threshold;
                case "EQ":  return Math.abs(actual - threshold) < 0.0001;
                default:
                    log.warn("[CompositeTag] 未知 metric 操作符: {}", operator);
                    return false;
            }
        }

        return false;
    }
}
