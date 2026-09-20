package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.AiConversation;
import com.english.entity.AiMessage;
import com.english.entity.ConversationShare;
import com.english.exception.BusinessException;
import com.english.mapper.AiConversationMapper;
import com.english.mapper.AiMessageMapper;
import com.english.mapper.ConversationShareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.english.datasource.UseMaster;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ConversationService {

    @Autowired
    private AiConversationMapper conversationMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    @Autowired
    private ConversationShareMapper shareMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.share.secret:langhack-share-secret-2026}")
    private String shareSecret;

    public AiConversation createConversation(Long userId, String firstQuestion) {
        AiConversation conv = new AiConversation();
        conv.setUserId(userId);
        String title = firstQuestion != null && firstQuestion.length() > 20
                ? firstQuestion.substring(0, 20) + "..."
                : firstQuestion;
        conv.setTitle(title);
        conv.setMessageCount(0);
        conversationMapper.insert(conv);
        return conv;
    }

    @Transactional
    public AiMessage addMessage(Long conversationId, Long userId, String role, String content,
                                String selectedText, String articleContext) {
        AiConversation conv = getOwnedConversation(conversationId, userId);

        AiMessage msg = new AiMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSelectedText(selectedText);
        msg.setArticleContext(articleContext);
        messageMapper.insert(msg);

        conv.setMessageCount(conv.getMessageCount() + 1);
        conversationMapper.updateById(conv);

        return msg;
    }

    public List<AiMessage> getMessages(Long conversationId, Long userId) {
        getOwnedConversation(conversationId, userId);
        QueryWrapper<AiMessage> qw = new QueryWrapper<>();
        qw.eq("conversation_id", conversationId).orderByAsc("created_time");
        return messageMapper.selectList(qw);
    }

    /** Bounded, owner-checked text history for a learner-authorized provider failover. */
    @UseMaster
    public List<AiMessage> getRecentMessagesBefore(Long conversationId, Long userId,
                                                    Long beforeMessageId, int limit) {
        getOwnedConversation(conversationId, userId);
        if (beforeMessageId == null) return Collections.emptyList();
        int safeLimit = Math.min(Math.max(limit, 1), 8);
        QueryWrapper<AiMessage> query = new QueryWrapper<>();
        query.select("id", "role", "content")
                .eq("conversation_id", conversationId)
                .lt("id", beforeMessageId)
                .in("role", "user", "assistant")
                .orderByDesc("id")
                .last("LIMIT " + safeLimit);
        List<AiMessage> messages = messageMapper.selectList(query);
        Collections.reverse(messages);
        return messages;
    }

    public Page<AiConversation> listUserConversations(Long userId, int page, int size) {
        Page<AiConversation> pageReq = new Page<>(page, size);
        QueryWrapper<AiConversation> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("updated_time");
        return conversationMapper.selectPage(pageReq, qw);
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        getOwnedConversation(conversationId, userId);
        conversationMapper.deleteById(conversationId);
    }

    public ConversationShare generateShareToken(Long conversationId, Long userId,
                                                Integer expiresInDays, String rawPassword) {
        getOwnedConversation(conversationId, userId);

        // 删除旧分享（每个对话只保留一个分享链接）
        QueryWrapper<ConversationShare> qw = new QueryWrapper<>();
        qw.eq("conversation_id", conversationId);
        shareMapper.delete(qw);

        String token = buildToken(conversationId, userId);

        ConversationShare share = new ConversationShare();
        share.setConversationId(conversationId);
        share.setShareToken(token);
        share.setCreatedBy(userId);
        share.setViewCount(0);
        if (expiresInDays != null && expiresInDays > 0) {
            share.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        }
        if (rawPassword != null && !rawPassword.isBlank()) {
            share.setAccessPassword(passwordEncoder.encode(rawPassword));
        }
        shareMapper.insert(share);
        return share;
    }

    @UseMaster
    public Map<String, Object> getSharedConversation(String token, String password) {
        QueryWrapper<ConversationShare> qw = new QueryWrapper<>();
        qw.eq("share_token", token);
        ConversationShare share = shareMapper.selectOne(qw);

        if (share == null) {
            throw new BusinessException("分享链接不存在或已失效");
        }
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("此分享链接已过期");
        }
        if (share.getAccessPassword() != null) {
            if (password == null || !passwordEncoder.matches(password, share.getAccessPassword())) {
                throw new BusinessException("访问密码错误，请重试");
            }
        }

        // 增加访问次数
        share.setViewCount(share.getViewCount() + 1);
        shareMapper.updateById(share);

        AiConversation conv = conversationMapper.selectById(share.getConversationId());
        if (conv == null) {
            throw new BusinessException("对话不存在");
        }

        QueryWrapper<AiMessage> mqw = new QueryWrapper<>();
        mqw.eq("conversation_id", conv.getId()).orderByAsc("created_time");
        List<AiMessage> messages = messageMapper.selectList(mqw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation", conv);
        result.put("messages", messages);
        result.put("viewCount", share.getViewCount());
        result.put("hasPassword", share.getAccessPassword() != null);
        return result;
    }

    public boolean shareRequiresPassword(String token) {
        QueryWrapper<ConversationShare> qw = new QueryWrapper<>();
        qw.eq("share_token", token).select("access_password", "expires_at");
        ConversationShare share = shareMapper.selectOne(qw);
        if (share == null) return false;
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) return false;
        return share.getAccessPassword() != null;
    }

    private AiConversation getOwnedConversation(Long conversationId, Long userId) {
        AiConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || conv.getDeleted() == 1) {
            throw new BusinessException("对话不存在");
        }
        if (!conv.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此对话");
        }
        return conv;
    }

    private String buildToken(Long conversationId, Long userId) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String hmacInput = conversationId + ":" + userId + ":" + System.currentTimeMillis();
        String hmac = hmacSha256(hmacInput, shareSecret).substring(0, 32);
        return uuid + hmac;
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC generation failed", e);
        }
    }
}
