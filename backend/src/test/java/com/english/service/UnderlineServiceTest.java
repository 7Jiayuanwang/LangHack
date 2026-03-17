package com.english.service;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Text;
import com.english.entity.Underline;
import com.english.mapper.UnderlineMapper;
import com.english.service.TextService;
import com.english.service.impl.UnderlineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnderlineServiceTest {

    @Mock
    private UnderlineMapper underlineMapper;

    @Mock
    private TextService textService;

    @InjectMocks
    private UnderlineServiceImpl underlineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createUnderline_ShouldInsertUnderline() {
        Long userId = 1L;
        Long textId = 100L;
        String selectedText = "example text";
        Integer startIndex = 0;
        Integer endIndex = 12;

        when(underlineMapper.insert(any(Underline.class))).thenReturn(1);

        Text mockText = new Text();
        mockText.setId(textId);
        mockText.setDeleted(0);
        mockText.setContent("This is an example text used for testing underline creation.");
        when(textService.getTextById(textId)).thenReturn(mockText);

        underlineService.createUnderline(userId, textId, selectedText, startIndex, endIndex, "serialized-range");

        verify(underlineMapper, times(1)).insert(any(Underline.class));
    }

    @Test
    void getUnderlinesByTextId_ShouldReturnList() {
        Long textId = 100L;
        Underline u1 = new Underline();
        u1.setTextId(textId);
        Underline u2 = new Underline();
        u2.setTextId(textId);
        List<Underline> expected = Arrays.asList(u1, u2);

        when(underlineMapper.selectList(any(QueryWrapper.class))).thenReturn(expected);

        List<Underline> result = underlineService.getUnderlinesByTextId(textId);

        assertEquals(2, result.size());
        verify(underlineMapper, times(1)).selectList(any(QueryWrapper.class));
    }
}
