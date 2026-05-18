package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.VocabularyBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface VocabularyBookMapper extends BaseMapper<VocabularyBook> {

    @Select("SELECT student_id, COUNT(*) as mastered_count FROM vocabulary_books WHERE mastery_level = 'MASTERED' GROUP BY student_id ORDER BY mastered_count DESC")
    List<Map<String, Object>> selectAllMasteredWordRanking();

    @Select("<script>SELECT student_id, COUNT(*) as mastered_count FROM vocabulary_books WHERE mastery_level = 'MASTERED' AND student_id IN <foreach collection='studentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY student_id ORDER BY mastered_count DESC</script>")
    List<Map<String, Object>> selectMasteredWordRankingByStudentIds(@Param("studentIds") List<Long> studentIds);
}