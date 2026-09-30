package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.WordCategoryMap;
import com.english.mapper.WordCategoryMapMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class WordCategoryImportService {

    private static final Logger logger = LoggerFactory.getLogger(WordCategoryImportService.class);

    @Autowired
    private WordCategoryMapMapper mapper;

    private static final Map<String, String> CSV_FILES = Map.of(
        "data/cet4.csv", "CET4",
        "data/cet6.csv", "CET6",
        "data/gaokao.csv", "GAOKAO"
    );

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Long count = mapper.selectCount(null);
        if (count != null && count > 0) {
            logger.info("word_category_map already has {} rows, skipping import", count);
            return;
        }
        doImport();
    }

    @Transactional
    public int reimport() {
        mapper.delete(null);
        return doImport();
    }

    private int doImport() {
        int total = 0;
        for (Map.Entry<String, String> entry : CSV_FILES.entrySet()) {
            int n = importCsv(entry.getKey(), entry.getValue());
            total += n;
            logger.info("Imported {} words for category {}", n, entry.getValue());
        }
        logger.info("word_category_map import complete: {} rows total", total);
        return total;
    }

    private int importCsv(String classpathFile, String category) {
        int count = 0;
        try {
            ClassPathResource res = new ClassPathResource(classpathFile);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; }
                    String word = extractWord(line);
                    if (word == null || word.isEmpty()) continue;
                    try {
                        mapper.insert(new WordCategoryMap(word.toLowerCase(), category));
                        count++;
                    } catch (Exception e) {
                        // duplicate key is expected for words in multiple lists
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to import CSV {}: {}", classpathFile, e.getMessage());
        }
        return count;
    }

    private String extractWord(String csvLine) {
        if (csvLine == null || csvLine.isEmpty()) return null;
        String[] parts = csvLine.split(",", 2);
        String word = parts[0].trim();
        if (word.startsWith("\"") && word.endsWith("\"")) {
            word = word.substring(1, word.length() - 1);
        }
        return word.trim();
    }
}
