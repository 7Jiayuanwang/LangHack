package com.english.service;

import com.english.exception.BusinessException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateRequest;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class TencentTranslateService {

    private static final Logger logger = LoggerFactory.getLogger(TencentTranslateService.class);

    @Value("${tencent.translate.secret-id:}")
    private String secretId;

    @Value("${tencent.translate.secret-key:}")
    private String secretKey;

    @Value("${tencent.translate.region:ap-guangzhou}")
    private String region;

    /** 单例 TmtClient，在 @PostConstruct 阶段初始化，复用于所有翻译请求 */
    private TmtClient tmtClient;

    /** 并发信号量：最多允许 10 个请求同时访问腾讯云翻译 API */
    private final Semaphore semaphore = new Semaphore(10);

    /**
     * Spring 完成依赖注入后初始化单例 TmtClient。
     * 若 secretId/secretKey 未配置则跳过初始化，translate() 中的配置检查会处理此情况。
     */
    @PostConstruct
    public void init() {
        if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            logger.warn("腾讯翻译 secretId/secretKey 未配置，TmtClient 将不会初始化");
            return;
        }
        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("tmt.tencentcloudapi.com");
        httpProfile.setConnTimeout(10); // 连接超时：10 秒
        httpProfile.setReadTimeout(10); // 读取超时：10 秒

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        this.tmtClient = new TmtClient(cred, region, clientProfile);
        logger.info("TmtClient 单例初始化完成，region={}", region);
    }

    /**
     * 翻译文本
     * @param text 要翻译的文本
     * @param source 源语言（en: 英语, zh: 中文, auto: 自动检测）
     * @param target 目标语言（en: 英语, zh: 中文）
     * @return 翻译结果
     */
    @CircuitBreaker(name = "tencent-translate", fallbackMethod = "translateFallback")
    public String translate(String text, String source, String target) {
        // 空文本直接返回
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 检查配置
        if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            String errorMsg = "翻译服务未配置";
            logger.error(errorMsg);
            throw new BusinessException(errorMsg);
        }

        // 获取并发许可，等待最多 5 秒
        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                logger.warn("Semaphore 获取超时，当前可用许可: {}", semaphore.availablePermits());
                throw new BusinessException("翻译服务繁忙，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("翻译请求被中断，请重试");
        }

        try {
            logger.debug("开始翻译 - 源语言: {}, 目标语言: {}, 文本长度: {}", source, target, text.length());

            // 实例化请求对象
            TextTranslateRequest req = new TextTranslateRequest();
            req.setSourceText(text);
            req.setSource(source);
            req.setTarget(target);
            req.setProjectId(0L);

            // 使用单例 TmtClient 发起请求
            TextTranslateResponse resp = tmtClient.TextTranslate(req);

            String result = resp.getTargetText();
            logger.debug("翻译成功，结果长度: {}", result != null ? result.length() : 0);
            return result != null ? result : "";

        } catch (TencentCloudSDKException e) {
            logger.error("腾讯云翻译API错误 - 错误码: {}", e.getErrorCode(), e);

            // 根据错误类型返回更友好的提示
            String msg = e.getMessage();
            if (msg != null && msg.contains("not authorized")) {
                throw new BusinessException("翻译服务权限不足，请检查腾讯云API密钥是否有机器翻译权限");
            } else if (msg != null && msg.contains("InvalidCredential")) {
                throw new BusinessException("翻译服务密钥无效，请检查配置");
            } else {
                throw new BusinessException("翻译失败，请稍后重试");
            }
        } finally {
            semaphore.release();
        }
    }

    private String translateFallback(String text, String source, String target, Exception e) {
        logger.warn("Tencent translate circuit breaker triggered: {}", e.getMessage());
        return "[翻译服务暂时不可用]";
    }

    /**
     * 英译中
     */
    public String translateEnToCn(String text) {
        return translate(text, "en", "zh");
    }

    /**
     * 中译英
     */
    public String translateCnToEn(String text) {
        return translate(text, "zh", "en");
    }

    /**
     * 自动检测语言并翻译
     */
    public String translateAuto(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 简单判断：如果包含中文字符，则翻译成英文，否则翻译成中文
        if (text.matches(".*[\\u4e00-\\u9fa5]+.*")) {
            return translate(text, "zh", "en");
        } else {
            return translate(text, "en", "zh");
        }
    }
}
