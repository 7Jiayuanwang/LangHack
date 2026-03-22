package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.AccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    void batchInsert(@Param("list") List<AccessLog> list);

    List<Map<String, Object>> aggregateHourly(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> topPagesByDateRange(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime,
                                                   @Param("limit") int limit);

    int deleteOlderThan(@Param("beforeTime") LocalDateTime beforeTime);
}
