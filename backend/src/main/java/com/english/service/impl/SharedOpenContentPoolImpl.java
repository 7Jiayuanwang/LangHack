package com.english.service.impl;

import com.english.service.SharedOpenContentPool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.PostConstruct;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 从固定的、平台级主题目录构建共享内容池。这里不读取任何用户数据；个性化只发生在本地重排阶段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedOpenContentPoolImpl implements SharedOpenContentPool {

    private static final Set<String> ACADEMIC_TOPICS = Set.of(
            "artificial intelligence", "computer science", "agriculture",
            "environmental science", "psychology");
    private static final Set<String> LIFE_SCIENCE_TOPICS = Set.of(
            "agriculture", "environmental science", "psychology");
    private static final Map<String, String> DEV_TOPIC_TAGS = devTopicTags();

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${agent.content-discovery.enabled:true}")
    private boolean enabled;

    @Value("${agent.content-discovery.wikipedia-enabled:true}")
    private boolean wikipediaEnabled;

    @Value("${agent.content-discovery.arxiv-enabled:true}")
    private boolean arxivEnabled;

    @Value("${agent.content-discovery.europe-pmc-enabled:true}")
    private boolean europePmcEnabled;

    @Value("${agent.content-discovery.dev-community-enabled:true}")
    private boolean devCommunityEnabled;

    @Value("${agent.content-discovery.connect-timeout-ms:2000}")
    private long connectTimeoutMs;

    @Value("${agent.content-discovery.read-timeout-ms:3500}")
    private long readTimeoutMs;

    @Value("${agent.content-discovery.wikipedia-api:https://en.wikipedia.org/w/api.php}")
    private String wikipediaApi;

    @Value("${agent.content-discovery.arxiv-api:https://export.arxiv.org/api/query}")
    private String arxivApi;

    @Value("${agent.content-discovery.europe-pmc-api:https://www.ebi.ac.uk/europepmc/webservices/rest/search}")
    private String europePmcApi;

    @Value("${agent.content-discovery.dev-community-api:https://dev.to/api/articles}")
    private String devCommunityApi;

    @Value("${agent.content-discovery.catalog-topics:technology,artificial intelligence,computer science,agriculture,environmental science,business,psychology,culture,sports science}")
    private String catalogTopicsRaw;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile List<ContentCandidate> snapshot = Collections.emptyList();
    private RestTemplate restTemplate;

    @PostConstruct
    void initializeClient() {
        restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.max(250, connectTimeoutMs)))
                .setReadTimeout(Duration.ofMillis(Math.max(500, readTimeoutMs)))
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "LangHack/1.0 (English learning shared content pool)")
                .build();
    }

    @Override
    public List<ContentCandidate> currentCandidates() {
        return new ArrayList<>(snapshot);
    }

    @Scheduled(
            fixedDelayString = "${agent.content-discovery.refresh-ms:21600000}",
            initialDelayString = "${agent.content-discovery.initial-delay-ms:30000}")
    public void refreshSharedPool() {
        if (!enabled || !refreshing.compareAndSet(false, true)) return;
        try {
            List<ContentCandidate> refreshed = new ArrayList<>();
            List<String> catalogTopics = catalogTopics();
            for (String topic : catalogTopics) {
                if (wikipediaEnabled) refreshed.addAll(safelyFetchWikipedia(topic, 2));
            }
            List<String> academicTopics = catalogTopics.stream()
                    .filter(ACADEMIC_TOPICS::contains)
                    .collect(Collectors.toList());
            if (arxivEnabled && !academicTopics.isEmpty()) {
                refreshed.addAll(safelyFetchArxivCatalog(academicTopics));
            }
            List<String> lifeScienceTopics = catalogTopics.stream()
                    .filter(LIFE_SCIENCE_TOPICS::contains)
                    .collect(Collectors.toList());
            if (europePmcEnabled && !lifeScienceTopics.isEmpty()) {
                refreshed.addAll(safelyFetchEuropePmcCatalog(lifeScienceTopics));
            }
            if (devCommunityEnabled) {
                refreshed.addAll(safelyFetchDevCommunityCatalog(catalogTopics));
            }
            List<ContentCandidate> cleaned = refreshed.stream()
                    .filter(this::isUsable)
                    .collect(Collectors.toMap(
                            candidate -> normalizeUrl(candidate.getUrl()),
                            candidate -> candidate,
                            (first, ignored) -> first,
                            LinkedHashMap::new))
                    .values().stream()
                    .collect(Collectors.toList());
            if (!cleaned.isEmpty()) {
                snapshot = Collections.unmodifiableList(cleaned);
                log.info("共享英文内容池刷新完成: topics={}, candidates={}", catalogTopics.size(), cleaned.size());
            } else {
                log.warn("共享英文内容池刷新未获得有效内容，保留上一版本 candidates={}", snapshot.size());
            }
        } finally {
            refreshing.set(false);
        }
    }

    private List<String> catalogTopics() {
        if (catalogTopicsRaw == null || catalogTopicsRaw.isBlank()) return Collections.emptyList();
        return java.util.Arrays.stream(catalogTopicsRaw.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<ContentCandidate> safelyFetchWikipedia(String catalogTopic, int limit) {
        try {
            URI url = UriComponentsBuilder.fromHttpUrl(wikipediaApi)
                    .queryParam("action", "query")
                    .queryParam("generator", "search")
                    .queryParam("gsrsearch", catalogTopic)
                    .queryParam("gsrlimit", limit)
                    .queryParam("gsrnamespace", 0)
                    .queryParam("prop", "extracts|info")
                    .queryParam("exintro", 1)
                    .queryParam("explaintext", 1)
                    .queryParam("exsentences", 3)
                    .queryParam("inprop", "url")
                    .queryParam("format", "json")
                    .queryParam("formatversion", 2)
                    .build().encode().toUri();
            String body = restTemplate.getForObject(url, String.class);
            JsonNode pages = objectMapper.readTree(body).path("query").path("pages");
            if (!pages.isArray()) return Collections.emptyList();

            List<ContentCandidate> result = new ArrayList<>();
            for (JsonNode page : pages) {
                String title = page.path("title").asText("").trim();
                String summary = cleanWhitespace(page.path("extract").asText(""));
                String pageUrl = page.path("fullurl").asText("").trim();
                if (!title.isEmpty() && !pageUrl.isEmpty()) {
                    result.add(new ContentCandidate(title, trim(summary, 520), pageUrl,
                            "Wikipedia", "ENCYCLOPEDIA", catalogTopic, null));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("共享 Wikipedia 主题刷新失败: topic={}, reason={}", catalogTopic, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ContentCandidate> safelyFetchArxivCatalog(List<String> catalogTopics) {
        try {
            String searchQuery = catalogTopics.stream()
                    .map(topic -> "all:\"" + topic + "\"")
                    .collect(Collectors.joining(" OR ", "(", ")"));
            int limit = Math.min(10, Math.max(3, catalogTopics.size() * 2));
            URI url = UriComponentsBuilder.fromHttpUrl(arxivApi)
                    .queryParam("search_query", searchQuery)
                    .queryParam("start", 0)
                    .queryParam("max_results", limit)
                    .queryParam("sortBy", "submittedDate")
                    .queryParam("sortOrder", "descending")
                    .build().encode().toUri();
            String body = restTemplate.getForObject(url, String.class);
            if (body == null || body.isBlank()) return Collections.emptyList();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            NodeList entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
            List<ContentCandidate> result = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String title = cleanWhitespace(text(entry, "title"));
                String summary = cleanWhitespace(text(entry, "summary"));
                String pageUrl = alternateLink(entry);
                LocalDateTime published = parseTime(text(entry, "published"));
                if (!title.isEmpty() && !pageUrl.isEmpty()) {
                    String matchedTopic = matchCatalogTopic(title + " " + summary, catalogTopics);
                    result.add(new ContentCandidate(title, trim(summary, 520), pageUrl,
                            "arXiv", "RESEARCH", matchedTopic, published));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("共享 arXiv 学术目录刷新失败: topics={}, reason={}", catalogTopics, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ContentCandidate> safelyFetchEuropePmcCatalog(List<String> catalogTopics) {
        try {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String topicQuery = catalogTopics.stream()
                    .map(topic -> "\"" + topic + "\"")
                    .collect(Collectors.joining(" OR ", "(", ")"));
            String searchQuery = topicQuery
                    + " AND OPEN_ACCESS:Y AND LANG:eng"
                    + " AND FIRST_PDATE:[" + today.minusMonths(18) + " TO " + today + "]"
                    + " sort_date:y";
            int limit = Math.min(12, Math.max(4, catalogTopics.size() * 3));
            URI url = UriComponentsBuilder.fromHttpUrl(europePmcApi)
                    .queryParam("query", searchQuery)
                    .queryParam("format", "json")
                    .queryParam("pageSize", limit)
                    .queryParam("resultType", "core")
                    .build().encode().toUri();
            String body = restTemplate.getForObject(url, String.class);
            JsonNode items = objectMapper.readTree(body).path("resultList").path("result");
            if (!items.isArray()) return Collections.emptyList();

            List<ContentCandidate> result = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("id").asText("").trim();
                String source = item.path("source").asText("").trim();
                String title = cleanMarkup(item.path("title").asText(""));
                String summary = cleanMarkup(item.path("abstractText").asText(""));
                if (id.isEmpty() || source.isEmpty() || title.isEmpty()) continue;
                String pageUrl = "https://europepmc.org/article/" + source + "/" + id;
                String matchedTopic = matchCatalogTopic(title + " " + summary, catalogTopics);
                LocalDateTime published = parseDate(item.path("firstPublicationDate").asText(""));
                result.add(new ContentCandidate(title, trim(summary, 520), pageUrl,
                        "Europe PMC", "LIFE_SCIENCE", matchedTopic, published));
            }
            return result;
        } catch (Exception e) {
            log.warn("共享 Europe PMC 目录刷新失败: topics={}, reason={}", catalogTopics, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ContentCandidate> safelyFetchDevCommunityCatalog(List<String> catalogTopics) {
        List<ContentCandidate> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : DEV_TOPIC_TAGS.entrySet()) {
            if (!catalogTopics.contains(entry.getValue())) continue;
            result.addAll(safelyFetchDevCommunityTag(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private List<ContentCandidate> safelyFetchDevCommunityTag(String tag, String catalogTopic) {
        try {
            URI url = UriComponentsBuilder.fromHttpUrl(devCommunityApi)
                    .queryParam("tag", tag)
                    .queryParam("top", 30)
                    .queryParam("per_page", 6)
                    .build().encode().toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.forem.api-v1+json")));
            String body = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody();
            JsonNode items = objectMapper.readTree(body);
            if (!items.isArray()) return Collections.emptyList();

            List<ContentCandidate> result = new ArrayList<>();
            for (JsonNode item : items) {
                if (!"en".equalsIgnoreCase(item.path("language").asText("en"))) continue;
                if (item.path("positive_reactions_count").asInt(0) < 5) continue;
                String title = cleanMarkup(item.path("title").asText(""));
                String summary = cleanMarkup(item.path("description").asText(""));
                String pageUrl = item.path("url").asText("").trim();
                LocalDateTime published = parseTime(item.path("published_timestamp").asText(""));
                if (!title.isEmpty() && !pageUrl.isEmpty()) {
                    result.add(new ContentCandidate(title, trim(summary, 520), pageUrl,
                            "DEV Community", "COMMUNITY", catalogTopic, published));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("共享 DEV Community 目录刷新失败: tag={}, reason={}", tag, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String matchCatalogTopic(String content, List<String> catalogTopics) {
        String haystack = content == null ? "" : content.toLowerCase(Locale.ROOT);
        for (String topic : catalogTopics) {
            if (haystack.contains(topic)) return topic;
        }
        for (String topic : catalogTopics) {
            boolean tokenMatch = java.util.Arrays.stream(topic.split(" "))
                    .filter(token -> token.length() >= 5)
                    .anyMatch(haystack::contains);
            if (tokenMatch) return topic;
        }
        return catalogTopics.get(0);
    }

    private String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("http://www.w3.org/2005/Atom", localName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private String alternateLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "link");
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (node instanceof Element) {
                Element link = (Element) node;
                if ("alternate".equals(link.getAttribute("rel"))) return link.getAttribute("href");
            }
        }
        return text(entry, "id");
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(value.trim()), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isUsable(ContentCandidate candidate) {
        if (candidate == null || candidate.getTitle() == null || candidate.getUrl() == null) return false;
        String url = candidate.getUrl().toLowerCase(Locale.ROOT);
        if (!(url.startsWith("https://en.wikipedia.org/") || url.startsWith("https://arxiv.org/")
                || url.startsWith("http://arxiv.org/") || url.startsWith("https://europepmc.org/")
                || url.startsWith("https://dev.to/"))) return false;
        String title = candidate.getTitle().trim();
        return title.length() >= 4 && title.length() <= 240 && englishRatio(title) >= 0.65;
    }

    private double englishRatio(String value) {
        int letters = 0;
        int latin = 0;
        for (char ch : value.toCharArray()) {
            if (Character.isLetter(ch)) {
                letters++;
                if (ch <= 127) latin++;
            }
        }
        return letters == 0 ? 0 : (double) latin / letters;
    }

    private String normalizeUrl(String url) {
        return url == null ? "" : url.trim().replace("http://arxiv.org/", "https://arxiv.org/");
    }

    private String cleanWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String cleanMarkup(String value) {
        if (value == null || value.isBlank()) return "";
        return cleanWhitespace(HtmlUtils.htmlUnescape(value.replaceAll("<[^>]+>", " ")));
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 1).trim() + "…";
    }

    private static Map<String, String> devTopicTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("programming", "computer science");
        tags.put("ai", "artificial intelligence");
        return Collections.unmodifiableMap(tags);
    }
}
