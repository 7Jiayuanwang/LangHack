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

    /** Quartz/Spring cron 表达式，例如 "0 0 3 * * ?" */
    private String cronExpr;

    /** 是否启用定时自动更新 */
    private Boolean enabled;

    /** 跟踪的 git 分支 */
    private String gitBranch;

    /** 更新前是否自动备份数据库 */
    private Boolean backupBeforeUpdate;

    /** 最近一次执行时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次执行状态：NEVER / RUNNING / SUCCESS / FAILED */
    private String lastRunStatus;
}
