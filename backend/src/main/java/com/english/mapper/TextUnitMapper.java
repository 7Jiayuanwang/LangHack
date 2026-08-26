package com.english.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TextUnitMapper {
    @Select("SELECT unit_id FROM text_units WHERE text_id = #{textId} ORDER BY order_num, unit_id")
    List<Long> selectUnitIdsByTextId(@Param("textId") Long textId);

    @Select("SELECT text_id FROM text_units WHERE unit_id = #{unitId} ORDER BY order_num, text_id")
    List<Long> selectTextIdsByUnitId(@Param("unitId") Long unitId);

    @Insert("INSERT IGNORE INTO text_units (text_id, unit_id, order_num) VALUES (#{textId}, #{unitId}, #{orderNum})")
    int insertRelation(@Param("textId") Long textId, @Param("unitId") Long unitId, @Param("orderNum") Integer orderNum);

    @Delete("DELETE FROM text_units WHERE text_id = #{textId} AND unit_id = #{unitId}")
    int deleteRelation(@Param("textId") Long textId, @Param("unitId") Long unitId);

    @Delete("DELETE FROM text_units WHERE text_id = #{textId}")
    int deleteAllRelations(@Param("textId") Long textId);

    @Delete("DELETE FROM text_units WHERE unit_id = #{unitId}")
    int deleteAllRelationsByUnitId(@Param("unitId") Long unitId);
}
