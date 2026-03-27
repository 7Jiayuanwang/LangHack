package com.english.service;

import com.english.entity.AiProvider;

import java.util.List;
import java.util.Map;

public interface AiProviderService {

    /** 查询所有服务商（按 service, weight 降序） */
    List<AiProvider> listAll();

    /** 查询指定服务下所有服务商（按 weight 降序） */
    List<AiProvider> listByService(String service);

    /** 新增服务商 */
    void create(AiProvider provider);

    /** 更新服务商（apiKey 为 null 时保留原值） */
    void update(AiProvider provider);

    /** 删除服务商 */
    void delete(Long id);

    /** 启用 / 禁用 */
    void setEnabled(Long id, int enabled, String operator);

    /** 加权轮询选出一个可用服务商（无可用时抛异常） */
    AiProvider pick(String service);

    /** 测试指定服务商连通性，返回 {success, input, output, latencyMs, error} */
    Map<String, Object> test(Long id, String testText);

    /** 从环境变量将 AI 密钥种子化到 ai_provider 表（已存在则跳过） */
    void seedFromEnv();
}
