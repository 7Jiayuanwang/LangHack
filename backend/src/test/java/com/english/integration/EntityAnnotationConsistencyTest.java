package com.english.integration;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.english.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies entity annotation consistency across the codebase.
 *
 * Catches issues such as:
 * - Missing @TableLogic on entities that should support soft-delete
 * - @TableLogic on entities that should use physical delete
 * - Missing @TableName or incorrect table names
 * - @JsonProperty WRITE_ONLY on sensitive fields (password)
 * - Consistent @TableId AUTO strategy
 */
@DisplayName("Entity Annotation Consistency Tests")
class EntityAnnotationConsistencyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Entities that MUST have @TableLogic soft-delete
    private static final List<Class<?>> SOFT_DELETE_ENTITIES = List.of(
        User.class, Text.class, Word.class, Course.class,
        com.english.entity.Class.class, Media.class, Assignment.class
    );

    // Entities that must NOT have @TableLogic (physical delete or no delete)
    private static final List<Class<?>> NO_SOFT_DELETE_ENTITIES = List.of(
        Unit.class, UnitWord.class, StudentClass.class, Underline.class,
        VocabularyBook.class, LearningRecord.class, TextLearningRecord.class,
        ScoreRecord.class, StudentExerciseAnswer.class, ExerciseOption.class
    );

    // All entities with their expected table names
    static Stream<Arguments> entityTableNameProvider() {
        return Stream.of(
            Arguments.of(User.class, "users"),
            Arguments.of(Text.class, "texts"),
            Arguments.of(Word.class, "words"),
            Arguments.of(Course.class, "courses"),
            Arguments.of(com.english.entity.Class.class, "classes"),
            Arguments.of(Media.class, "media"),
            Arguments.of(Assignment.class, "assignments"),
            Arguments.of(Unit.class, "units"),
            Arguments.of(UnitWord.class, "unit_words"),
            Arguments.of(StudentClass.class, "student_classes"),
            Arguments.of(Underline.class, "underlines"),
            Arguments.of(VocabularyBook.class, "vocabulary_books"),
            Arguments.of(LearningRecord.class, "learning_records"),
            Arguments.of(TextLearningRecord.class, "text_learning_records"),
            Arguments.of(ScoreRecord.class, "score_records"),
            Arguments.of(StudentExerciseAnswer.class, "student_exercise_answers"),
            Arguments.of(TextExercise.class, "text_exercises"),
            Arguments.of(ExerciseOption.class, "exercise_options")
        );
    }

    // ========== @TableLogic Tests ==========

    @Test
    @DisplayName("All soft-delete entities have @TableLogic on 'deleted' field")
    void softDeleteEntities_haveTableLogic() {
        for (Class<?> entityClass : SOFT_DELETE_ENTITIES) {
            Field deletedField = findField(entityClass, "deleted");
            assertNotNull(deletedField,
                entityClass.getSimpleName() + " must have a 'deleted' field");

            TableLogic tableLogic = deletedField.getAnnotation(TableLogic.class);
            assertNotNull(tableLogic,
                entityClass.getSimpleName() + ".deleted must have @TableLogic annotation");

            assertEquals("0", tableLogic.value(),
                entityClass.getSimpleName() + " @TableLogic value must be '0'");
            assertEquals("1", tableLogic.delval(),
                entityClass.getSimpleName() + " @TableLogic delval must be '1'");
        }
    }

    @Test
    @DisplayName("Non-soft-delete entities do NOT have @TableLogic")
    void nonSoftDeleteEntities_noTableLogic() {
        for (Class<?> entityClass : NO_SOFT_DELETE_ENTITIES) {
            Field deletedField = findField(entityClass, "deleted");
            if (deletedField != null) {
                TableLogic tableLogic = deletedField.getAnnotation(TableLogic.class);
                assertNull(tableLogic,
                    entityClass.getSimpleName() + " should NOT have @TableLogic (physical delete)");
            }
            // If no 'deleted' field exists, that's fine — entity doesn't support delete
        }
    }

    // ========== @TableName Tests ==========

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("entityTableNameProvider")
    @DisplayName("Entity @TableName matches expected table")
    void entityTableName_matchesExpected(Class<?> entityClass, String expectedTable) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        assertNotNull(tableName,
            entityClass.getSimpleName() + " must have @TableName annotation");
        assertEquals(expectedTable, tableName.value(),
            entityClass.getSimpleName() + " @TableName value mismatch");
    }

    // ========== @TableId Tests ==========

    @Test
    @DisplayName("All entities with 'id' field use @TableId AUTO strategy")
    void allEntities_useAutoId() {
        List<Class<?>> allEntities = new ArrayList<>(SOFT_DELETE_ENTITIES);
        allEntities.addAll(NO_SOFT_DELETE_ENTITIES);
        allEntities.add(TextExercise.class);

        for (Class<?> entityClass : allEntities) {
            Field idField = findField(entityClass, "id");
            if (idField == null) continue;

            TableId tableId = idField.getAnnotation(TableId.class);
            assertNotNull(tableId,
                entityClass.getSimpleName() + ".id must have @TableId annotation");
            assertEquals(IdType.AUTO, tableId.type(),
                entityClass.getSimpleName() + " @TableId must use AUTO strategy");
        }
    }

    // ========== @JsonProperty Security Tests ==========

    @Test
    @DisplayName("User.password has @JsonProperty WRITE_ONLY (never serialized)")
    void userPassword_writeOnly() {
        Field passwordField = findField(User.class, "password");
        assertNotNull(passwordField, "User must have a 'password' field");

        JsonProperty jsonProperty = passwordField.getAnnotation(JsonProperty.class);
        assertNotNull(jsonProperty,
            "User.password must have @JsonProperty annotation");
        assertEquals(JsonProperty.Access.WRITE_ONLY, jsonProperty.access(),
            "User.password must be WRITE_ONLY to prevent serialization");
    }

    @Test
    @DisplayName("User password is not included in JSON serialization")
    void userPassword_notInJson() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("secret123");
        user.setRole("USER");

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("secret123"),
            "Password must not appear in serialized JSON");
        assertFalse(json.contains("password"),
            "Password field key must not appear in serialized JSON");
        assertTrue(json.contains("testuser"),
            "Username should appear in serialized JSON");
    }

    @Test
    @DisplayName("User password can be deserialized from JSON (WRITE_ONLY allows deserialization)")
    void userPassword_canDeserialize() throws Exception {
        String json = "{\"username\":\"newuser\",\"password\":\"pass123\",\"role\":\"USER\"}";

        User user = objectMapper.readValue(json, User.class);

        assertEquals("newuser", user.getUsername());
        assertEquals("pass123", user.getPassword(),
            "Password must be settable from JSON input (WRITE_ONLY allows deserialization)");
    }

    // ========== Timestamp Field Consistency ==========

    @Test
    @DisplayName("Soft-delete entities have timestamp fields")
    void softDeleteEntities_haveTimestamps() {
        for (Class<?> entityClass : SOFT_DELETE_ENTITIES) {
            boolean hasCreatedTime = findField(entityClass, "createdTime") != null
                || findField(entityClass, "createdAt") != null;
            boolean hasUpdatedTime = findField(entityClass, "updatedTime") != null
                || findField(entityClass, "updatedAt") != null;

            assertTrue(hasCreatedTime,
                entityClass.getSimpleName() + " must have createdTime or createdAt field");
            assertTrue(hasUpdatedTime,
                entityClass.getSimpleName() + " must have updatedTime or updatedAt field");
        }
    }

    // ========== Utility ==========

    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
