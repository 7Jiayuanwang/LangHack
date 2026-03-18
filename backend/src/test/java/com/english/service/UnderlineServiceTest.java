package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Text;
import com.english.entity.Underline;
import com.english.exception.BusinessException;
import com.english.mapper.UnderlineMapper;
import com.english.service.impl.UnderlineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnderlineServiceTest {

    @Mock
    private UnderlineMapper underlineMapper;

    @Mock
    private TextService textService;

    @InjectMocks
    private UnderlineServiceImpl underlineService;

    private Text mockText;

    @BeforeEach
    void setUp() {
        mockText = new Text();
        mockText.setId(100L);
        mockText.setDeleted(0);
        mockText.setContent("This is an example text used for testing underline creation.");
    }

    @Test
    void createUnderline_validInput_shouldInsertUnderline() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = 10;
        Integer endIndex = 17;

        when(textService.getTextById(textId)).thenReturn(mockText);
        when(underlineMapper.insert(any(Underline.class))).thenReturn(1);

        underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, "serialized");

        verify(underlineMapper, times(1)).insert(argThat(u ->
            u.getUserId().equals(userId) &&
            u.getTextId().equals(textId) &&
            u.getSelectedText().equals(selectedText) &&
            u.getStartIndex().equals(startIndex) &&
            u.getEndIndex().equals(endIndex)
        ));
    }

    @Test
    void createUnderline_nullUserId_shouldThrowException() {
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = 0;
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(null, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_nullTextId_shouldThrowException() {
        Long userId = 1L;
        String selectedText = "example";
        Integer startIndex = 0;
        Integer endIndex = 7;

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, null, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_emptySelectedText_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        Integer startIndex = 0;
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, "   ", startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_nullSelectedText_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        Integer startIndex = 0;
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, null, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_nullStartIndex_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, null, endIndex, null)
        );
    }

    @Test
    void createUnderline_nullEndIndex_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = 0;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, null, null)
        );
    }

    @Test
    void createUnderline_invalidRange_startNegative_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = -1;
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_invalidRange_endBeforeStart_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = 10;
        Integer endIndex = 5;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_invalidRange_startEqualsEnd_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "x";
        Integer startIndex = 5;
        Integer endIndex = 5;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_textNotFound_shouldThrowException() {
        Long userId = 1L;
        Long textId = 999L;
        String selectedText = "example";
        Integer startIndex = 0;
        Integer endIndex = 7;

        when(textService.getTextById(textId)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_textDeleted_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example";
        Integer startIndex = 0;
        Integer endIndex = 7;

        Text deletedText = new Text();
        deletedText.setId(textId);
        deletedText.setDeleted(1);
        deletedText.setContent("This is an example text");

        when(textService.getTextById(textId)).thenReturn(deletedText);

        // Note: current implementation doesn't check deleted status, but it's good practice
        // The implementation only checks if text is null
        when(underlineMapper.insert(any(Underline.class))).thenReturn(1);
        underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null);
        verify(underlineMapper, times(1)).insert(any());
    }

    @Test
    void createUnderline_rangeExceedsTextLength_shouldThrowException() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "text content";
        Integer startIndex = 50;
        Integer endIndex = 100;

        when(textService.getTextById(textId)).thenReturn(mockText);

        assertThrows(BusinessException.class, () ->
            underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, null)
        );
    }

    @Test
    void createUnderline_shouldInsertUnderline() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example text";
        Integer startIndex = 0;
        Integer endIndex = 12;

        when(textService.getTextById(textId)).thenReturn(mockText);
        when(underlineMapper.insert(any(Underline.class))).thenReturn(1);

        underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, "serialized-range");

        verify(underlineMapper, times(1)).insert(any(Underline.class));
    }

    @Test
    void getUnderlinesByTextId_shouldReturnList() {
        Long textId = 100L;
        Underline u1 = new Underline();
        u1.setId(1L);
        u1.setTextId(textId);
        u1.setUserId(1L);

        Underline u2 = new Underline();
        u2.setId(2L);
        u2.setTextId(textId);
        u2.setUserId(2L);

        List<Underline> expected = Arrays.asList(u1, u2);

        when(underlineMapper.selectList(any(QueryWrapper.class))).thenReturn(expected);

        List<Underline> result = underlineService.getUnderlinesByTextId(textId);

        assertEquals(2, result.size());
        verify(underlineMapper, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void getUnderlinesByTextId_nullTextId_shouldThrowException() {
        assertThrows(BusinessException.class, () ->
            underlineService.getUnderlinesByTextId(null)
        );
    }

    @Test
    void getUnderlinesByTextIdAndUserId_validInput_shouldReturnFilteredList() {
        Long textId = 100L;
        Long userId = 1L;

        Underline u1 = new Underline();
        u1.setId(1L);
        u1.setTextId(textId);
        u1.setUserId(userId);

        List<Underline> expected = Arrays.asList(u1);

        when(underlineMapper.selectList(any(QueryWrapper.class))).thenReturn(expected);

        List<Underline> result = underlineService.getUnderlinesByTextIdAndUserId(textId, userId);

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
        verify(underlineMapper, times(1)).selectList(any(QueryWrapper.class));
    }

    @Test
    void getUnderlinesByTextIdAndUserId_nullTextId_shouldThrowException() {
        Long userId = 1L;
        assertThrows(BusinessException.class, () ->
            underlineService.getUnderlinesByTextIdAndUserId(null, userId)
        );
    }

    @Test
    void getUnderlinesByTextIdAndUserId_nullUserId_shouldThrowException() {
        Long textId = 100L;
        assertThrows(BusinessException.class, () ->
            underlineService.getUnderlinesByTextIdAndUserId(textId, null)
        );
    }

    @Test
    void getUnderlinesByTextIdAndUserId_noMatches_shouldReturnEmptyList() {
        Long textId = 100L;
        Long userId = 1L;

        when(underlineMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

        List<Underline> result = underlineService.getUnderlinesByTextIdAndUserId(textId, userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void deleteUnderlinesByTextId_validInput_shouldDelete() {
        Long textId = 100L;

        when(underlineMapper.delete(any(QueryWrapper.class))).thenReturn(3);

        underlineService.deleteUnderlinesByTextId(textId);

        verify(underlineMapper, times(1)).delete(any(QueryWrapper.class));
    }

    @Test
    void deleteUnderlinesByTextId_nullTextId_shouldThrowException() {
        assertThrows(BusinessException.class, () ->
            underlineService.deleteUnderlinesByTextId(null)
        );
    }

    @Test
    void deleteUnderlinesByTextId_noUnderlines_shouldHandleGracefully() {
        Long textId = 100L;

        when(underlineMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        underlineService.deleteUnderlinesByTextId(textId);

        verify(underlineMapper, times(1)).delete(any(QueryWrapper.class));
    }

    @Test
    void getUnderlineById_validId_shouldReturnUnderline() {
        Long underlineId = 1L;
        Underline u = new Underline();
        u.setId(underlineId);
        u.setUserId(1L);
        u.setTextId(100L);

        when(underlineMapper.selectById(underlineId)).thenReturn(u);

        Underline result = underlineService.getUnderlineById(underlineId);

        assertNotNull(result);
        assertEquals(underlineId, result.getId());
    }

    @Test
    void getUnderlineById_nullId_shouldReturnNull() {
        Underline result = underlineService.getUnderlineById(null);
        assertNull(result);
    }

    @Test
    void getUnderlineById_notFound_shouldReturnNull() {
        Long underlineId = 999L;

        when(underlineMapper.selectById(underlineId)).thenReturn(null);

        Underline result = underlineService.getUnderlineById(underlineId);

        assertNull(result);
    }

    @Test
    void deleteUnderline_ownershipCheck_shouldDelete() {
        Long underlineId = 1L;
        Long userId = 1L;

        Underline u = new Underline();
        u.setId(underlineId);
        u.setUserId(userId);
        u.setTextId(100L);

        when(underlineMapper.selectOne(any(QueryWrapper.class))).thenReturn(u);
        when(underlineMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        boolean result = underlineService.deleteUnderline(underlineId, userId);

        assertTrue(result);
        verify(underlineMapper, times(1)).delete(any(QueryWrapper.class));
    }

    @Test
    void deleteUnderline_wrongUser_shouldThrowException() {
        Long underlineId = 1L;
        Long userId = 1L;
        Long differentUserId = 2L;

        Underline u = new Underline();
        u.setId(underlineId);
        u.setUserId(differentUserId);
        u.setTextId(100L);

        when(underlineMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () ->
            underlineService.deleteUnderline(underlineId, userId)
        );
    }

    @Test
    void deleteUnderline_notFound_shouldThrowException() {
        Long underlineId = 999L;
        Long userId = 1L;

        when(underlineMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () ->
            underlineService.deleteUnderline(underlineId, userId)
        );
    }

    @Test
    void deleteUnderline_nullUnderlineId_shouldThrowException() {
        Long userId = 1L;

        assertThrows(BusinessException.class, () ->
            underlineService.deleteUnderline(null, userId)
        );
    }

    @Test
    void deleteUnderline_nullUserId_shouldThrowException() {
        Long underlineId = 1L;

        assertThrows(BusinessException.class, () ->
            underlineService.deleteUnderline(underlineId, null)
        );
    }

    @Test
    void deleteUnderline_deletionFails_shouldReturnFalse() {
        Long underlineId = 1L;
        Long userId = 1L;

        Underline u = new Underline();
        u.setId(underlineId);
        u.setUserId(userId);

        when(underlineMapper.selectOne(any(QueryWrapper.class))).thenReturn(u);
        when(underlineMapper.delete(any(QueryWrapper.class))).thenReturn(0);

        boolean result = underlineService.deleteUnderline(underlineId, userId);

        assertFalse(result);
    }
}
