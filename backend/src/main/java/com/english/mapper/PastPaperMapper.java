package com.english.mapper;

import com.english.entity.PastPaper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PastPaperMapper {
    @Select("SELECT * FROM past_papers WHERE published = 1 ORDER BY exam_date DESC, id DESC")
    List<PastPaper> findPublished();

    @Select("SELECT * FROM past_papers ORDER BY id DESC")
    List<PastPaper> findAll();

    @Select("SELECT * FROM past_papers WHERE id = #{id}")
    PastPaper findById(Long id);

    @Insert("INSERT INTO past_papers (paper_key, category, title, exam_date, duration_minutes, scope, question_count, content_json, published, created_by) " +
            "VALUES (#{paperKey}, #{category}, #{title}, #{examDate}, #{durationMinutes}, #{scope}, #{questionCount}, #{contentJson}, #{published}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PastPaper paper);

    @Update("UPDATE past_papers SET paper_key = #{paperKey}, category = #{category}, title = #{title}, exam_date = #{examDate}, " +
            "duration_minutes = #{durationMinutes}, scope = #{scope}, question_count = #{questionCount}, content_json = #{contentJson}, " +
            "published = #{published} WHERE id = #{id}")
    int update(PastPaper paper);

    @Delete("DELETE FROM past_papers WHERE id = #{id}")
    int deleteById(Long id);
}
