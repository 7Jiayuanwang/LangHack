package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.Text;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TextMapper extends BaseMapper<Text> {

    /**
     * 软删除指定单元下所有未删除的课文（绕过 @TableLogic 的 SELECT 过滤）
     */
    @Update("UPDATE texts SET deleted = 1 WHERE unit_id = #{unitId} AND deleted = 0")
    int softDeleteByUnitId(@Param("unitId") Long unitId);

    /**
     * 清除所有课文的 unit_id 引用（包括已删除的课文，绕过 @TableLogic）
     * 用于删除单元前断开 FK 引用，防止 ON DELETE CASCADE 物理删除已软删除的课文
     */
    @Update("UPDATE texts SET unit_id = NULL WHERE unit_id = #{unitId}")
    int clearUnitReference(@Param("unitId") Long unitId);
}