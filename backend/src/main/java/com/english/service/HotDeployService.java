package com.english.service;

import com.english.dto.HotDeployRequest;

import java.util.Map;

public interface HotDeployService {

    /** 当前 blue/green 后端健康状态 + 当前 commit + 上次部署时间 */
    Map<String, Object> getStatus();

    /**
     * 异步触发一次零停机热更新。
     *
     * @return 部署 ID（用于后续轮询日志）；不支持时返回 null
     */
    String trigger(HotDeployRequest req);

    /** 拉取部署日志 + 当前步骤索引 + 状态（RUNNING/SUCCESS/FAILED:rc） */
    Map<String, Object> getLogs(String deployId, int fromLine);

    /** 是否在 docker 环境内部署 + 是否具备触发热更新的条件 */
    boolean isSupported();

    /** 不支持时的原因（前端可展示） */
    String getUnsupportedReason();
}
