package com.english.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MyMetaObjectHandler.
 *
 * Verifies that createdTime/createdAt/updatedTime/updatedAt/joinTime
 * are auto-filled on insert and updatedTime/updatedAt on update.
 */
class MyMetaObjectHandlerTest {

    private MyMetaObjectHandler handler;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EntityWithCreatedTime.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithCreatedAt.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithUpdatedTime.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithUpdatedAt.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithJoinTime.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithBothUpdated.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithAllFields.class);
        TableInfoHelper.initTableInfo(assistant, EntityWithoutTimeFields.class);
    }

    @BeforeEach
    void setUp() {
        handler = new MyMetaObjectHandler();
    }

    // ==================== Interface Implementation ====================

    @Test
    @DisplayName("MyMetaObjectHandler should implement MetaObjectHandler")
    void shouldImplementMetaObjectHandler() {
        assertInstanceOf(MetaObjectHandler.class, handler);
    }

    // ==================== Insert Fill Tests ====================

    @Nested
    @DisplayName("insertFill tests")
    class InsertFillTests {

        @Test
        @DisplayName("insertFill should fill createdTime when field exists and is null")
        void insertFill_shouldFillCreatedTime() {
            EntityWithCreatedTime entity = new EntityWithCreatedTime();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            handler.insertFill(metaObject);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertNotNull(entity.getCreatedTime(), "createdTime should be filled");
            assertTrue(entity.getCreatedTime().isAfter(before), "createdTime should be after test start");
            assertTrue(entity.getCreatedTime().isBefore(after), "createdTime should be before test end");
        }

        @Test
        @DisplayName("insertFill should fill createdAt when field exists and is null")
        void insertFill_shouldFillCreatedAt() {
            EntityWithCreatedAt entity = new EntityWithCreatedAt();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertNotNull(entity.getCreatedAt(), "createdAt should be filled");
        }

        @Test
        @DisplayName("insertFill should fill updatedTime when field exists and is null")
        void insertFill_shouldFillUpdatedTime() {
            EntityWithUpdatedTime entity = new EntityWithUpdatedTime();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertNotNull(entity.getUpdatedTime(), "updatedTime should be filled on insert");
        }

        @Test
        @DisplayName("insertFill should fill updatedAt when field exists and is null")
        void insertFill_shouldFillUpdatedAt() {
            EntityWithUpdatedAt entity = new EntityWithUpdatedAt();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertNotNull(entity.getUpdatedAt(), "updatedAt should be filled on insert");
        }

        @Test
        @DisplayName("insertFill should fill joinTime when field exists and is null")
        void insertFill_shouldFillJoinTime() {
            EntityWithJoinTime entity = new EntityWithJoinTime();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertNotNull(entity.getJoinTime(), "joinTime should be filled on insert");
        }

        @Test
        @DisplayName("insertFill should fill all time fields simultaneously")
        void insertFill_shouldFillAllFieldsSimultaneously() {
            EntityWithAllFields entity = new EntityWithAllFields();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertNotNull(entity.getCreatedTime(), "createdTime should be filled");
            assertNotNull(entity.getCreatedAt(), "createdAt should be filled");
            assertNotNull(entity.getUpdatedTime(), "updatedTime should be filled");
            assertNotNull(entity.getUpdatedAt(), "updatedAt should be filled");
            assertNotNull(entity.getJoinTime(), "joinTime should be filled");
        }

        @Test
        @DisplayName("insertFill should NOT overwrite createdTime if already set (strictInsertFill)")
        void insertFill_shouldNotOverwriteExistingCreatedTime() {
            EntityWithCreatedTime entity = new EntityWithCreatedTime();
            LocalDateTime existingTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            entity.setCreatedTime(existingTime);
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertEquals(existingTime, entity.getCreatedTime(),
                    "strictInsertFill should not overwrite existing value");
        }

        @Test
        @DisplayName("insertFill should NOT overwrite updatedTime if already set (strictInsertFill)")
        void insertFill_shouldNotOverwriteExistingUpdatedTime() {
            EntityWithUpdatedTime entity = new EntityWithUpdatedTime();
            LocalDateTime existingTime = LocalDateTime.of(2020, 6, 15, 12, 0);
            entity.setUpdatedTime(existingTime);
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.insertFill(metaObject);

            assertEquals(existingTime, entity.getUpdatedTime(),
                    "strictInsertFill should not overwrite existing updatedTime");
        }

        @Test
        @DisplayName("insertFill should do nothing for entity without time fields")
        void insertFill_shouldDoNothingForEntityWithoutTimeFields() {
            EntityWithoutTimeFields entity = new EntityWithoutTimeFields();
            entity.setName("test");
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            // Should not throw
            assertDoesNotThrow(() -> handler.insertFill(metaObject));
            assertEquals("test", entity.getName());
        }
    }

    // ==================== Update Fill Tests ====================

    @Nested
    @DisplayName("updateFill tests")
    class UpdateFillTests {

        @Test
        @DisplayName("updateFill should fill updatedTime when field exists and is null")
        void updateFill_shouldFillUpdatedTime() {
            EntityWithUpdatedTime entity = new EntityWithUpdatedTime();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertNotNull(entity.getUpdatedTime(), "updatedTime should be filled on update");
        }

        @Test
        @DisplayName("updateFill should fill updatedAt when field exists and is null")
        void updateFill_shouldFillUpdatedAt() {
            EntityWithUpdatedAt entity = new EntityWithUpdatedAt();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertNotNull(entity.getUpdatedAt(), "updatedAt should be filled on update");
        }

        @Test
        @DisplayName("updateFill should NOT fill createdTime (only update fields)")
        void updateFill_shouldNotFillCreatedTime() {
            EntityWithAllFields entity = new EntityWithAllFields();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertNull(entity.getCreatedTime(), "createdTime should NOT be filled on update");
            assertNull(entity.getCreatedAt(), "createdAt should NOT be filled on update");
            assertNotNull(entity.getUpdatedTime(), "updatedTime should be filled on update");
            assertNotNull(entity.getUpdatedAt(), "updatedAt should be filled on update");
        }

        @Test
        @DisplayName("updateFill should NOT overwrite updatedTime if already set (strictUpdateFill)")
        void updateFill_shouldNotOverwriteExistingUpdatedTime() {
            EntityWithUpdatedTime entity = new EntityWithUpdatedTime();
            LocalDateTime existingTime = LocalDateTime.of(2020, 1, 1, 0, 0);
            entity.setUpdatedTime(existingTime);
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            handler.updateFill(metaObject);

            assertEquals(existingTime, entity.getUpdatedTime(),
                    "strictUpdateFill should not overwrite existing value");
        }

        @Test
        @DisplayName("updateFill should fill both updatedTime and updatedAt simultaneously")
        void updateFill_shouldFillBothUpdatedFields() {
            EntityWithBothUpdated entity = new EntityWithBothUpdated();
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            handler.updateFill(metaObject);
            LocalDateTime after = LocalDateTime.now().plusSeconds(1);

            assertNotNull(entity.getUpdatedTime());
            assertNotNull(entity.getUpdatedAt());
            assertTrue(entity.getUpdatedTime().isAfter(before));
            assertTrue(entity.getUpdatedAt().isAfter(before));
            assertTrue(entity.getUpdatedTime().isBefore(after));
            assertTrue(entity.getUpdatedAt().isBefore(after));
        }

        @Test
        @DisplayName("updateFill should do nothing for entity without update fields")
        void updateFill_shouldDoNothingForEntityWithoutUpdateFields() {
            EntityWithoutTimeFields entity = new EntityWithoutTimeFields();
            entity.setName("test");
            MetaObject metaObject = SystemMetaObject.forObject(entity);

            assertDoesNotThrow(() -> handler.updateFill(metaObject));
            assertEquals("test", entity.getName());
        }
    }

    // ==================== Test Helper Entities ====================

    /**
     * Minimal POJO with createdTime field (matching handler's "createdTime" string).
     */
    @TableName("test_created_time")
    public static class EntityWithCreatedTime {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    }

    @TableName("test_created_at")
    public static class EntityWithCreatedAt {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    @TableName("test_updated_time")
    public static class EntityWithUpdatedTime {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    }

    @TableName("test_updated_at")
    public static class EntityWithUpdatedAt {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    @TableName("test_join_time")
    public static class EntityWithJoinTime {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime joinTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getJoinTime() { return joinTime; }
        public void setJoinTime(LocalDateTime joinTime) { this.joinTime = joinTime; }
    }

    @TableName("test_both_updated")
    public static class EntityWithBothUpdated {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedTime;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    @TableName("test_all_fields")
    public static class EntityWithAllFields {
        @TableId
        private Long id;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdTime;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedTime;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime joinTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public LocalDateTime getCreatedTime() { return createdTime; }
        public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public LocalDateTime getJoinTime() { return joinTime; }
        public void setJoinTime(LocalDateTime joinTime) { this.joinTime = joinTime; }
    }

    @TableName("test_no_time_fields")
    public static class EntityWithoutTimeFields {
        @TableId
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
