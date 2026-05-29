package com.english.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("update_config")
public class UpdateConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 是否启用定时热部署 */
    private Boolean enabled;

    /** Spring Cron 表达式，例如 "0 0 2 ? * SUN" */
    private String cronExpr;

    /** 跟踪的 git 分支 */
    private String gitBranch;

    /** 最近一次执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次执行状态：NEVER / RUNNING / SUCCESS / FAILED */
    private String lastRunStatus;
}
