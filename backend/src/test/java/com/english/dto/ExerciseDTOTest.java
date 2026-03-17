package com.english.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExerciseDTO and ExerciseOptionDTO.
 *
 * Covers constructors, getter/setter, validation annotations,
 * and relationship between ExerciseDTO and ExerciseOptionDTO.
 */
class ExerciseDTOTest {

    private ExerciseDTO dto;
    private Validator validator;

    @BeforeEach
    void setUp() {
        dto = new ExerciseDTO();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ExerciseDTO Constructor Tests ====================

    @Nested
    @DisplayName("ExerciseDTO constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("no-arg constructor should create non-null instance")
        void noArgConstructor_shouldCreateNonNull() {
            assertNotNull(new ExerciseDTO());
        }

        @Test
        @DisplayName("no-arg constructor should have all null fields")
        void noArgConstructor_shouldHaveAllNullFields() {
            ExerciseDTO exerciseDTO = new ExerciseDTO();
            assertNull(exerciseDTO.getId());
            assertNull(exerciseDTO.getQuestion());
            assertNull(exerciseDTO.getExerciseType());
            assertNull(exerciseDTO.getOrderNum());
            assertNull(exerciseDTO.getOptions());
        }

        @Test
        @DisplayName("parameterized constructor should set fields correctly")
        void parameterizedConstructor_shouldSetFields() {
            List<ExerciseOptionDTO> options = new ArrayList<>();
            ExerciseDTO exerciseDTO = new ExerciseDTO("What is Java?", "SINGLE_CHOICE", 1, options);

            assertEquals("What is Java?", exerciseDTO.getQuestion());
            assertEquals("SINGLE_CHOICE", exerciseDTO.getExerciseType());
            assertEquals(1, exerciseDTO.getOrderNum());
            assertSame(options, exerciseDTO.getOptions());
        }

        @Test
        @DisplayName("parameterized constructor should accept null values")
        void parameterizedConstructor_shouldAcceptNulls() {
            ExerciseDTO exerciseDTO = new ExerciseDTO(null, null, null, null);
            assertNull(exerciseDTO.getQuestion());
            assertNull(exerciseDTO.getExerciseType());
            assertNull(exerciseDTO.getOrderNum());
            assertNull(exerciseDTO.getOptions());
        }

        @Test
        @DisplayName("parameterized constructor should NOT set id")
        void parameterizedConstructor_shouldNotSetId() {
            ExerciseDTO exerciseDTO = new ExerciseDTO("q", "t", 1, new ArrayList<>());
            assertNull(exerciseDTO.getId(), "Id should be null (not in constructor params)");
        }
    }

    // ==================== ExerciseDTO Getter/Setter Tests ====================

    @Nested
    @DisplayName("ExerciseDTO getter/setter tests")
    class GetterSetterTests {

        @Test
        @DisplayName("setId/getId should work correctly")
        void setGetId_shouldWork() {
            dto.setId(100L);
            assertEquals(100L, dto.getId());
        }

        @Test
        @DisplayName("setId with null should set null")
        void setId_withNull_shouldSetNull() {
            dto.setId(1L);
            dto.setId(null);
            assertNull(dto.getId());
        }

        @Test
        @DisplayName("setQuestion/getQuestion should work correctly")
        void setGetQuestion_shouldWork() {
            dto.setQuestion("What is Spring Boot?");
            assertEquals("What is Spring Boot?", dto.getQuestion());
        }

        @Test
        @DisplayName("setExerciseType/getExerciseType should work correctly")
        void setGetExerciseType_shouldWork() {
            dto.setExerciseType("MULTIPLE_CHOICE");
            assertEquals("MULTIPLE_CHOICE", dto.getExerciseType());
        }

        @Test
        @DisplayName("setOrderNum/getOrderNum should work correctly")
        void setGetOrderNum_shouldWork() {
            dto.setOrderNum(5);
            assertEquals(5, dto.getOrderNum());
        }

        @Test
        @DisplayName("setOptions/getOptions should work correctly")
        void setGetOptions_shouldWork() {
            List<ExerciseOptionDTO> options = Arrays.asList(
                    new ExerciseOptionDTO("Option A", "A", false, 1),
                    new ExerciseOptionDTO("Option B", "B", true, 2)
            );
            dto.setOptions(options);
            assertEquals(2, dto.getOptions().size());
            assertEquals("Option A", dto.getOptions().get(0).getOptionContent());
        }

        @Test
        @DisplayName("setOptions with empty list should work")
        void setOptions_withEmptyList_shouldWork() {
            dto.setOptions(Collections.emptyList());
            assertNotNull(dto.getOptions());
            assertTrue(dto.getOptions().isEmpty());
        }

        @Test
        @DisplayName("setOptions with null should set null")
        void setOptions_withNull_shouldSetNull() {
            dto.setOptions(null);
            assertNull(dto.getOptions());
        }
    }

    // ==================== ExerciseDTO Validation Tests ====================

    @Nested
    @DisplayName("ExerciseDTO validation tests")
    class ValidationTests {

        @Test
        @DisplayName("valid ExerciseDTO should have no violations")
        void validDTO_shouldHaveNoViolations() {
            ExerciseDTO validDTO = new ExerciseDTO(
                    "What is Java?", "SINGLE_CHOICE", 1,
                    Arrays.asList(new ExerciseOptionDTO("A", "A", true, 1))
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(validDTO);
            assertTrue(violations.isEmpty(), "Valid DTO should have no violations");
        }

        @Test
        @DisplayName("blank question should violate @NotBlank")
        void blankQuestion_shouldViolate() {
            ExerciseDTO invalidDTO = new ExerciseDTO(
                    "", "SINGLE_CHOICE", 1, new ArrayList<>()
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(invalidDTO);
            assertFalse(violations.isEmpty(), "Blank question should violate @NotBlank");
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("question")));
        }

        @Test
        @DisplayName("null question should violate @NotBlank")
        void nullQuestion_shouldViolate() {
            ExerciseDTO invalidDTO = new ExerciseDTO(
                    null, "SINGLE_CHOICE", 1, new ArrayList<>()
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(invalidDTO);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("blank exerciseType should violate @NotBlank")
        void blankExerciseType_shouldViolate() {
            ExerciseDTO invalidDTO = new ExerciseDTO(
                    "Question?", "", 1, new ArrayList<>()
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(invalidDTO);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("exerciseType")));
        }

        @Test
        @DisplayName("null orderNum should violate @NotNull")
        void nullOrderNum_shouldViolate() {
            ExerciseDTO invalidDTO = new ExerciseDTO(
                    "Question?", "SINGLE_CHOICE", null, new ArrayList<>()
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(invalidDTO);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("orderNum")));
        }

        @Test
        @DisplayName("null options should violate @NotNull")
        void nullOptions_shouldViolate() {
            ExerciseDTO invalidDTO = new ExerciseDTO(
                    "Question?", "SINGLE_CHOICE", 1, null
            );

            Set<ConstraintViolation<ExerciseDTO>> violations = validator.validate(invalidDTO);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("options")));
        }
    }

    // ==================== ExerciseOptionDTO Tests ====================

    @Nested
    @DisplayName("ExerciseOptionDTO tests")
    class ExerciseOptionDTOTests {

        @Test
        @DisplayName("no-arg constructor should create non-null instance")
        void noArgConstructor_shouldCreateNonNull() {
            assertNotNull(new ExerciseOptionDTO());
        }

        @Test
        @DisplayName("no-arg constructor should have all null fields")
        void noArgConstructor_shouldHaveAllNullFields() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            assertNull(option.getId());
            assertNull(option.getOptionContent());
            assertNull(option.getOptionKey());
            assertNull(option.getIsCorrect());
            assertNull(option.getOrderNum());
        }

        @Test
        @DisplayName("parameterized constructor should set fields correctly")
        void parameterizedConstructor_shouldSetFields() {
            ExerciseOptionDTO option = new ExerciseOptionDTO("Option A", "A", true, 1);
            assertEquals("Option A", option.getOptionContent());
            assertEquals("A", option.getOptionKey());
            assertTrue(option.getIsCorrect());
            assertEquals(1, option.getOrderNum());
        }

        @Test
        @DisplayName("setId/getId should work correctly")
        void setGetId_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setId(50L);
            assertEquals(50L, option.getId());
        }

        @Test
        @DisplayName("setOptionContent/getOptionContent should work correctly")
        void setGetOptionContent_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setOptionContent("This is option A content");
            assertEquals("This is option A content", option.getOptionContent());
        }

        @Test
        @DisplayName("setOptionKey/getOptionKey should work correctly")
        void setGetOptionKey_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setOptionKey("B");
            assertEquals("B", option.getOptionKey());
        }

        @Test
        @DisplayName("setIsCorrect/getIsCorrect should work with true")
        void setGetIsCorrect_true_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setIsCorrect(true);
            assertTrue(option.getIsCorrect());
        }

        @Test
        @DisplayName("setIsCorrect/getIsCorrect should work with false")
        void setGetIsCorrect_false_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setIsCorrect(false);
            assertFalse(option.getIsCorrect());
        }

        @Test
        @DisplayName("setIsCorrect with null should set null")
        void setIsCorrect_withNull_shouldSetNull() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setIsCorrect(null);
            assertNull(option.getIsCorrect());
        }

        @Test
        @DisplayName("setOrderNum/getOrderNum should work correctly")
        void setGetOrderNum_shouldWork() {
            ExerciseOptionDTO option = new ExerciseOptionDTO();
            option.setOrderNum(3);
            assertEquals(3, option.getOrderNum());
        }
    }

    // ==================== ExerciseOptionDTO Validation Tests ====================

    @Nested
    @DisplayName("ExerciseOptionDTO validation tests")
    class OptionValidationTests {

        @Test
        @DisplayName("valid option should have no violations")
        void validOption_shouldHaveNoViolations() {
            ExerciseOptionDTO option = new ExerciseOptionDTO("Content", "A", true, 1);
            Set<ConstraintViolation<ExerciseOptionDTO>> violations = validator.validate(option);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("blank optionContent should violate @NotBlank")
        void blankOptionContent_shouldViolate() {
            ExerciseOptionDTO option = new ExerciseOptionDTO("", "A", true, 1);
            Set<ConstraintViolation<ExerciseOptionDTO>> violations = validator.validate(option);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("optionContent")));
        }

        @Test
        @DisplayName("null isCorrect should violate @NotNull")
        void nullIsCorrect_shouldViolate() {
            ExerciseOptionDTO option = new ExerciseOptionDTO("Content", "A", null, 1);
            Set<ConstraintViolation<ExerciseOptionDTO>> violations = validator.validate(option);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("isCorrect")));
        }
    }
}
