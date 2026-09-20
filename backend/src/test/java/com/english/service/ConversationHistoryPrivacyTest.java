package com.english.service;

import com.english.entity.AiConversation;
import com.english.exception.BusinessException;
import com.english.mapper.AiConversationMapper;
import com.english.mapper.AiMessageMapper;
import com.english.mapper.ConversationShareMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryPrivacyTest {
    @Mock private AiConversationMapper conversationMapper;
    @Mock private AiMessageMapper messageMapper;
    @Mock private ConversationShareMapper shareMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private ConversationService service;

    @Test
    void refusesToReadAnotherLearnersHistory() {
        AiConversation conversation = new AiConversation();
        conversation.setId(42L);
        conversation.setUserId(8L);
        conversation.setDeleted(0);
        when(conversationMapper.selectById(42L)).thenReturn(conversation);

        assertThrows(BusinessException.class,
                () -> service.getRecentMessagesBefore(42L, 7L, 100L, 8));
        verifyNoInteractions(messageMapper);
    }
}
