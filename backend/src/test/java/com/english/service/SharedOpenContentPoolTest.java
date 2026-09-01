package com.english.service;

import com.english.service.impl.SharedOpenContentPoolImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SharedOpenContentPoolTest {

    @Test
    void refreshUsesOnlyFixedCatalogTopicAndPublishesCleanCandidate() {
        SharedOpenContentPoolImpl pool = configuredPool("technology", false);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(pool, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("gsrsearch=technology")))
                .andRespond(withSuccess(wikipediaResponse(), MediaType.APPLICATION_JSON));

        pool.refreshSharedPool();

        List<SharedOpenContentPool.ContentCandidate> candidates = pool.currentCandidates();
        assertEquals(1, candidates.size());
        assertEquals("technology", candidates.get(0).getCatalogTopic());
        assertTrue(candidates.get(0).getUrl().startsWith("https://en.wikipedia.org/"));
        server.verify();
    }

    @Test
    void parsesArxivAtomFromFixedAcademicCatalog() {
        SharedOpenContentPoolImpl pool = configuredPool("artificial intelligence", true);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(pool, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("gsrsearch=artificial%20intelligence")))
                .andRespond(withSuccess("{\"query\":{\"pages\":[]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("artificial%20intelligence")))
                .andRespond(withSuccess(arxivResponse(), MediaType.APPLICATION_ATOM_XML));

        pool.refreshSharedPool();

        List<SharedOpenContentPool.ContentCandidate> candidates = pool.currentCandidates();
        assertEquals(1, candidates.size());
        assertEquals("arXiv", candidates.get(0).getSourceName());
        assertEquals("RESEARCH", candidates.get(0).getSourceType());
        server.verify();
    }

    @Test
    void parsesOpenAccessEuropePmcResultsFromFixedCatalog() {
        SharedOpenContentPoolImpl pool = configuredPool("agriculture", false);
        ReflectionTestUtils.setField(pool, "wikipediaEnabled", false);
        ReflectionTestUtils.setField(pool, "europePmcEnabled", true);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(pool, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("OPEN_ACCESS:Y")))
                .andRespond(withSuccess(europePmcResponse(), MediaType.APPLICATION_JSON));

        pool.refreshSharedPool();

        List<SharedOpenContentPool.ContentCandidate> candidates = pool.currentCandidates();
        assertEquals(1, candidates.size());
        assertEquals("Europe PMC", candidates.get(0).getSourceName());
        assertEquals("LIFE_SCIENCE", candidates.get(0).getSourceType());
        assertEquals("agriculture", candidates.get(0).getCatalogTopic());
        assertTrue(candidates.get(0).getUrl().startsWith("https://europepmc.org/article/"));
        server.verify();
    }

    @Test
    void keepsOnlyPopularEnglishDevCommunityArticles() {
        SharedOpenContentPoolImpl pool = configuredPool("computer science", false);
        ReflectionTestUtils.setField(pool, "wikipediaEnabled", false);
        ReflectionTestUtils.setField(pool, "devCommunityEnabled", true);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(pool, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(containsString("tag=programming")))
                .andRespond(withSuccess(devCommunityResponse(), MediaType.APPLICATION_JSON));

        pool.refreshSharedPool();

        List<SharedOpenContentPool.ContentCandidate> candidates = pool.currentCandidates();
        assertEquals(1, candidates.size());
        assertEquals("DEV Community", candidates.get(0).getSourceName());
        assertEquals("COMMUNITY", candidates.get(0).getSourceType());
        assertEquals("computer science", candidates.get(0).getCatalogTopic());
        server.verify();
    }

    private SharedOpenContentPoolImpl configuredPool(String catalog, boolean arxivEnabled) {
        SharedOpenContentPoolImpl pool = new SharedOpenContentPoolImpl(
                new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(pool, "enabled", true);
        ReflectionTestUtils.setField(pool, "wikipediaEnabled", true);
        ReflectionTestUtils.setField(pool, "arxivEnabled", arxivEnabled);
        ReflectionTestUtils.setField(pool, "europePmcEnabled", false);
        ReflectionTestUtils.setField(pool, "devCommunityEnabled", false);
        ReflectionTestUtils.setField(pool, "connectTimeoutMs", 1000L);
        ReflectionTestUtils.setField(pool, "readTimeoutMs", 1000L);
        ReflectionTestUtils.setField(pool, "wikipediaApi", "https://en.wikipedia.org/w/api.php");
        ReflectionTestUtils.setField(pool, "arxivApi", "https://export.arxiv.org/api/query");
        ReflectionTestUtils.setField(pool, "europePmcApi", "https://www.ebi.ac.uk/europepmc/webservices/rest/search");
        ReflectionTestUtils.setField(pool, "devCommunityApi", "https://dev.to/api/articles");
        ReflectionTestUtils.setField(pool, "catalogTopicsRaw", catalog);
        ReflectionTestUtils.invokeMethod(pool, "initializeClient");
        return pool;
    }

    private String wikipediaResponse() {
        return "{\"query\":{\"pages\":[{"
                + "\"title\":\"Technology\","
                + "\"extract\":\"Technology is the application of conceptual knowledge.\","
                + "\"fullurl\":\"https://en.wikipedia.org/wiki/Technology\"}]}}";
    }

    private String arxivResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\">"
                + "<entry><id>http://arxiv.org/abs/2608.00001v1</id>"
                + "<title>Useful Artificial Intelligence Research</title>"
                + "<summary>A useful research summary for English learners.</summary>"
                + "<published>2026-08-28T17:53:48Z</published>"
                + "<link href=\"https://arxiv.org/abs/2608.00001v1\" rel=\"alternate\" type=\"text/html\"/>"
                + "</entry></feed>";
    }

    private String europePmcResponse() {
        return "{\"resultList\":{\"result\":[{"
                + "\"id\":\"PMC123456\",\"source\":\"PMC\","
                + "\"title\":\"Sustainable Agriculture for a Changing Climate\","
                + "\"abstractText\":\"<p>Agriculture research for resilient food systems.</p>\","
                + "\"firstPublicationDate\":\"2026-08-20\"}]}}";
    }

    private String devCommunityResponse() {
        return "[{\"title\":\"Five Programming Patterns Worth Learning\","
                + "\"description\":\"Practical patterns for computer science students.\","
                + "\"url\":\"https://dev.to/example/programming-patterns\","
                + "\"language\":\"en\",\"positive_reactions_count\":42,"
                + "\"published_timestamp\":\"2026-08-29T08:00:00Z\"},"
                + "{\"title\":\"Unreviewed Draft\",\"description\":\"Too little feedback.\","
                + "\"url\":\"https://dev.to/example/unreviewed\","
                + "\"language\":\"en\",\"positive_reactions_count\":1}]";
    }
}
