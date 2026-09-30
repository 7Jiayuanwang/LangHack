package com.english.service;

import com.english.entity.Text;
import com.english.service.impl.RecommendationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationVisibilityTest {
    @Test
    void neverMaterializesPrivateOrUnapprovedUserContentFromSimilarityCandidates() {
        TextService texts = mock(TextService.class);
        Text publicText = text(1L, 9L, 1, "APPROVED");
        Text privateText = text(2L, 9L, 0, "APPROVED");
        Text pendingText = text(3L, 9L, 1, "PENDING");
        Text systemText = text(4L, null, null, null);
        when(texts.listByIds(any())).thenReturn(List.of(publicText, privateText, pendingText, systemText));
        RecommendationServiceImpl service = new RecommendationServiceImpl();
        ReflectionTestUtils.setField(service, "textService", texts);

        List<Text> result = ReflectionTestUtils.invokeMethod(service, "materializeInOrder",
                new LinkedHashSet<>(List.of(1L, 2L, 3L, 4L)));

        assertEquals(List.of(1L, 4L), result.stream().map(Text::getId).toList());
    }

    private Text text(Long id, Long uploadedBy, Integer isPublic, String status) {
        Text item = new Text();
        item.setId(id);
        item.setUploadedBy(uploadedBy);
        item.setIsPublic(isPublic);
        item.setStatus(status);
        item.setDeleted(0);
        return item;
    }
}
