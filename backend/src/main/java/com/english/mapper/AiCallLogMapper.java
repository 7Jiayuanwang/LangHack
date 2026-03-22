package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    void batchInsert(@Param("list") List<AiCallLog> list);

    List<Map<String, Object>> aggregateDaily(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> getRealtimeStats(@Param("todayStart") LocalDateTime todayStart);

    int deleteOlderThan(@Param("beforeTime") LocalDateTime beforeTime);
}
