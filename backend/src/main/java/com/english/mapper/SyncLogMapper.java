package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.SyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 同步日志Mapper接口
 */
@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLog> {

    /**
     * 查询指定数据源最近一次同步日志
     */
    @Select("SELECT * FROM sync_logs WHERE data_source = #{dataSource} ORDER BY created_at DESC LIMIT 1")
    SyncLog selectLatestByDataSource(@Param("dataSource") String dataSource);

    /**
     * 查询指定数据源最近N条同步日志
     */
    @Select("SELECT * FROM sync_logs WHERE data_source = #{dataSource} ORDER BY created_at DESC LIMIT #{limit}")
    List<SyncLog> selectRecentByDataSource(@Param("dataSource") String dataSource, @Param("limit") int limit);

    /**
     * 查询所有数据源最近一次同步日志
     */
    @Select("SELECT s.* FROM sync_logs s INNER JOIN " +
            "(SELECT data_source, MAX(created_at) AS max_created_at FROM sync_logs GROUP BY data_source) latest " +
            "ON s.data_source = latest.data_source AND s.created_at = latest.max_created_at")
    List<SyncLog> selectLatestPerDataSource();
}
