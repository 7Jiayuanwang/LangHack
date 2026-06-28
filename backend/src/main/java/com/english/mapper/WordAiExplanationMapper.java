package com.english.mapper;

import com.english.entity.WordAiExplanation;
import org.apache.ibatis.annotations.*;

/**
 * 单词 AI 详细介绍 Mapper
 */
@Mapper
public interface WordAiExplanationMapper {

    @Select("SELECT * FROM word_ai_explanations WHERE word = #{word}")
    WordAiExplanation findByWord(String word);

    @Insert("INSERT INTO word_ai_explanations (word, phonetic, word_class, basic_meaning, " +
            "detailed_explanation, etymology, usage_examples, synonyms, antonyms, collocations) " +
            "VALUES (#{word}, #{phonetic}, #{wordClass}, #{basicMeaning}, #{detailedExplanation}, " +
            "#{etymology}, #{usageExamples}, #{synonyms}, #{antonyms}, #{collocations})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WordAiExplanation explanation);

    @Update("UPDATE word_ai_explanations SET phonetic = #{phonetic}, word_class = #{wordClass}, " +
            "basic_meaning = #{basicMeaning}, detailed_explanation = #{detailedExplanation}, " +
            "etymology = #{etymology}, usage_examples = #{usageExamples}, synonyms = #{synonyms}, " +
            "antonyms = #{antonyms}, collocations = #{collocations} WHERE word = #{word}")
    int update(WordAiExplanation explanation);

    @Delete("DELETE FROM word_ai_explanations WHERE word = #{word}")
    int deleteByWord(String word);
}
