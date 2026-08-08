package com.english.service;

public interface HiAgentService {

    /**
     * Generate CET-4 version of an article
     */
    String generateCET4Version(String originalContent);

    /**
     * Generate CET-6 version of an article
     */
    String generateCET6Version(String originalContent);

    /**
     * Generate Academic version of an article
     */
    String generateAcademicVersion(String originalContent);

    /**
     * Generate Chinese-English parallel version
     */
    String generateChineseParallelVersion(String originalContent);

    // Markdown support

    String generateCET4VersionMarkdown(String originalMarkdownContent);

    String generateCET6VersionMarkdown(String originalMarkdownContent);

    String generateAcademicVersionMarkdown(String originalMarkdownContent);

    String generateChineseParallelVersionMarkdown(String originalMarkdownContent);

    /**
     * AI Q&A: answer a user question about highlighted text in an article
     */
    String chatQA(String userQuestion, String selectedText, String articleContext);

    /**
     * AI Quiz: generate multiple-choice questions for an article using quiz-specific credentials
     */
    String generateQuiz(String title, String content, int count);

    /**
     * Create a conversation using QA credentials (exposed for streaming controller)
     */
    String createConversationForQA() throws Exception;

    /**
     * Stream chat query using QA credentials — returns SSE event stream
     */
    org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> chatQueryStreamingWithQACredentials(String conversationId, String prompt) throws Exception;
}
