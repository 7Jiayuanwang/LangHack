package com.english.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.english.entity.SentenceFavorite;
import com.english.entity.Text;
import com.english.exception.BusinessException;
import com.english.mapper.SentenceFavoriteMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.english.mapper.UnitMapper;
import com.english.service.impl.SentenceFavoriteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentenceFavoriteServiceTest {

    @Mock private SentenceFavoriteMapper favoriteMapper;
    @Mock private TextMapper textMapper;
    @Mock private TextUnitMapper textUnitMapper;
    @Mock private UnitMapper unitMapper;
    @Mock private StudentClassesService studentClassesService;
    @Mock private ClassService classService;
    @InjectMocks private SentenceFavoriteServiceImpl service;

    @Test
    void togglePublicTextCreatesNormalizedFavorite() {
        Text text = new Text();
        text.setId(10L);
        text.setIsPublic(1);
        when(textMapper.selectById(10L)).thenReturn(text);
        when(favoriteMapper.selectOne(any())).thenReturn(null);
        when(favoriteMapper.insert(any())).thenAnswer(invocation -> {
            SentenceFavorite favorite = invocation.getArgument(0);
            favorite.setId(99L);
            return 1;
        });

        Map<String, Object> result = service.toggle(3L, 10L, "  Example sentence.  ", false);

        assertEquals(true, result.get("favorited"));
        assertEquals(99L, result.get("id"));
        verify(favoriteMapper).insert(org.mockito.ArgumentMatchers.argThat(favorite ->
                favorite.getSentence().equals("Example sentence.") && favorite.getUserId().equals(3L)));
    }

    @Test
    void toggleRejectsInvalidTextId() {
        assertThrows(BusinessException.class, () -> service.toggle(3L, 0L, "Example", false));
    }

    @Test
    void toggleRejectsPrivateTextFromAnotherUser() {
        Text text = new Text();
        text.setId(10L);
        text.setUploadedBy(4L);
        text.setUnitId(999L);
        when(textMapper.selectById(10L)).thenReturn(text);
        when(textUnitMapper.selectUnitIdsByTextId(10L)).thenReturn(java.util.List.of());

        assertThrows(BusinessException.class, () -> service.toggle(3L, 10L, "Example", false));
    }

    @Test
    void deleteScopesOperationToCurrentUser() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(1);

        service.delete(8L, 3L);

        verify(favoriteMapper).delete(any(Wrapper.class));
    }

    @Test
    void deleteRejectsMissingOrForeignFavorite() {
        when(favoriteMapper.delete(any(Wrapper.class))).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.delete(8L, 3L));
    }
}
