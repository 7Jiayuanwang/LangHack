package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.english.entity.FeatureOverride;
import com.english.entity.UserTag;
import com.english.mapper.FeatureOverrideMapper;
import com.english.mapper.UserTagMapper;
import com.english.service.impl.FeatureToggleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeatureToggleServiceImpl.
 *
 * Priority: user-level > tag-level > global default.
 * Cache TTL = 60s; evicted on write.
 */
@ExtendWith(MockitoExtension.class)
class FeatureToggleServiceTest {

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private UserTagMapper userTagMapper;

    @Mock
    private FeatureOverrideMapper featureOverrideMapper;

    @InjectMocks
    private FeatureToggleServiceImpl service;

    private static final Long USER_ID = 42L;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> globalMap(String... kvPairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            m.put(kvPairs[i], kvPairs[i + 1]);
        }
        return m;
    }

    private FeatureOverride override(String scope, String scopeValue, String key, int enabled) {
        FeatureOverride o = new FeatureOverride();
        o.setScope(scope);
        o.setScopeValue(scopeValue);
        o.setFeatureKey(key);
        o.setEnabled(enabled);
        return o;
    }

    // ─── getMergedTogglesForUser ────────────────────────────────────────────────

    @Nested
    @DisplayName("getMergedTogglesForUser() 三层合并")
    class MergeTests {

        @Test
        @DisplayName("无标签无覆盖 → 仅返回全局值")
        void globalOnly() {
            when(systemConfigService.getByGroup("feature_toggle"))
                    .thenReturn(globalMap("page_vocab", "true", "page_ai_video", "false"));
            when(userTagMapper.selectTagsByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(featureOverrideMapper.selectList(any())).thenReturn(Collections.emptyList());

            Map<String, Boolean> result = service.getMergedTogglesForUser(USER_ID);

            assertEquals(true, result.get("page_vocab"));
            assertEquals(false, result.get("page_ai_video"));
        }

        @Test
        @DisplayName("标签覆盖 → 覆盖全局值")
        void tagOverridesGlobal() {
            when(systemConfigService.getByGroup("feature_toggle"))
                    .thenReturn(globalMap("page_ai_video", "false"));
            when(userTagMapper.selectTagsByUserId(USER_ID)).thenReturn(List.of("beta"));
            // tag-level override: page_ai_video = true
            when(featureOverrideMapper.selectList(any()))
                    .thenReturn(List.of(override("tag", "beta", "page_ai_video", 1)))
                    .thenReturn(Collections.emptyList()); // user-level: none

            Map<String, Boolean> result = service.getMergedTogglesForUser(USER_ID);

            assertTrue(result.get("page_ai_video"), "tag override should enable page_ai_video");
        }

        @Test
        @DisplayName("用户覆盖 → 优先于标签覆盖")
        void userOverrideWinsOverTag() {
            when(systemConfigService.getByGroup("feature_toggle"))
                    .thenReturn(globalMap("page_ai_video", "false"));
            when(userTagMapper.selectTagsByUserId(USER_ID)).thenReturn(List.of("beta"));
            // tag says true, user says false
            when(featureOverrideMapper.selectList(any()))
                    .thenReturn(List.of(override("tag", "beta", "page_ai_video", 1)))
                    .thenReturn(List.of(override("user", USER_ID.toString(), "page_ai_video", 0)));

            Map<String, Boolean> result = service.getMergedTogglesForUser(USER_ID);

            assertFalse(result.get("page_ai_video"), "user-level false should win over tag-level true");
        }

        @Test
        @DisplayName("enabled=0 的覆盖规则 → 返回 false")
        void enabledZeroReturnsFalse() {
            when(systemConfigService.getByGroup("feature_toggle"))
                    .thenReturn(globalMap("page_vocab", "true"));
            when(userTagMapper.selectTagsByUserId(USER_ID)).thenReturn(Collections.emptyList());
            when(featureOverrideMapper.selectList(any()))
                    .thenReturn(List.of(override("user", USER_ID.toString(), "page_vocab", 0)));

            Map<String, Boolean> result = service.getMergedTogglesForUser(USER_ID);

            assertFalse(result.get("page_vocab"));
        }
    }

    // ─── 标签管理 ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("标签管理方法")
    class TagManagementTests {

        @Test
        @DisplayName("listAllTagNames() 委托给 mapper")
        void listAllTagNames() {
            when(userTagMapper.selectAllTagNames()).thenReturn(List.of("beta", "vip"));
            assertEquals(List.of("beta", "vip"), service.listAllTagNames());
        }

        @Test
        @DisplayName("getUserIdsByTag() 委托给 mapper")
        void getUserIdsByTag() {
            when(userTagMapper.selectUserIdsByTag("beta")).thenReturn(List.of(1L, 2L));
            assertEquals(List.of(1L, 2L), service.getUserIdsByTag("beta"));
        }

        @Test
        @DisplayName("getUserTags() 委托给 mapper")
        void getUserTags() {
            when(userTagMapper.selectTagsByUserId(USER_ID)).thenReturn(List.of("beta"));
            assertEquals(List.of("beta"), service.getUserTags(USER_ID));
        }

        @Test
        @DisplayName("setUserTags() 先删后插，并清除用户缓存")
        void setUserTags() {
            when(userTagMapper.delete(any())).thenReturn(0);
            when(userTagMapper.insert(any())).thenReturn(1);

            service.setUserTags(USER_ID, List.of("beta", "vip"), "ops");

            verify(userTagMapper).delete(any());
            verify(userTagMapper, times(2)).insert(any(UserTag.class));
        }

        @Test
        @DisplayName("setUserTags() 空列表 → 只删不插")
        void setUserTagsEmpty() {
            when(userTagMapper.delete(any())).thenReturn(0);

            service.setUserTags(USER_ID, Collections.emptyList(), "ops");

            verify(userTagMapper).delete(any());
            verify(userTagMapper, never()).insert(any());
        }

        @Test
        @DisplayName("deleteTag() 删除用户关联 + 覆盖规则 + 清缓存")
        void deleteTag() {
            when(userTagMapper.delete(any())).thenReturn(0);
            when(featureOverrideMapper.delete(any())).thenReturn(0);

            service.deleteTag("beta", "ops");

            verify(userTagMapper).delete(any());
            verify(featureOverrideMapper).delete(any());
        }
    }

    // ─── 覆盖规则管理 ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("覆盖规则管理方法")
    class OverrideManagementTests {

        @Test
        @DisplayName("listOverrides() 无过滤 → 查全部")
        void listOverridesNoFilter() {
            when(featureOverrideMapper.selectList(any())).thenReturn(Collections.emptyList());
            service.listOverrides(null, null);
            verify(featureOverrideMapper).selectList(any());
        }

        @Test
        @DisplayName("upsertOverride() 已存在 → update，不 insert")
        void upsertExisting() {
            when(featureOverrideMapper.update(any(), any())).thenReturn(1);

            FeatureOverride o = override("tag", "beta", "page_ai_video", 1);
            service.upsertOverride(o, "ops");

            verify(featureOverrideMapper).update(any(), any());
            verify(featureOverrideMapper, never()).insert(any());
        }

        @Test
        @DisplayName("upsertOverride() 不存在 → insert")
        void upsertNew() {
            when(featureOverrideMapper.update(any(), any())).thenReturn(0);
            when(featureOverrideMapper.insert(any())).thenReturn(1);

            FeatureOverride o = override("tag", "beta", "page_ai_video", 1);
            service.upsertOverride(o, "ops");

            verify(featureOverrideMapper).insert(any());
        }

        @Test
        @DisplayName("deleteOverride() 调用 mapper.delete")
        void deleteOverride() {
            when(featureOverrideMapper.delete(any())).thenReturn(1);

            service.deleteOverride("tag", "beta", "page_ai_video", "ops");

            verify(featureOverrideMapper).delete(any());
        }
    }
}
