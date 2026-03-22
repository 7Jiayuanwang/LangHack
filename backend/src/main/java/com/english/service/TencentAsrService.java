package com.english.service;

import com.english.exception.BusinessException;
import com.tencentcloudapi.asr.v20190614.AsrClient;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionRequest;
import com.tencentcloudapi.asr.v20190614.models.SentenceRecognitionResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.english.service.impl.AiCallMetricsService;

import javax.annotation.PostConstruct;

@Service
public class TencentAsrService {

    private static final Logger logger = LoggerFactory.getLogger(TencentAsrService.class);

    // FIX: 使用正确的配置属性名 (tencent.asr.* 而非 tencent.translate.*)
    @Value("${tencent.asr.secret-id:}")
    private String secretId;

    @Value("${tencent.asr.secret-key:}")
    private String secretKey;

    @Value("${tencent.asr.region:ap-guangzhou}")
    private String region;

    private AsrClient asrClient;

    @Autowired(required = false)
    private AiCallMetricsService aiCallMetricsService;

    @PostConstruct
    public void init() {
        if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            logger.warn("腾讯 ASR secretId/secretKey 未配置，AsrClient 将不会初始化");
            return;
        }
        
        try {
            Credential cred = new Credential(secretId, secretKey);

            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("asr.tencentcloudapi.com");
            // FIX: 添加超时配置，防止请求无限期挂起
            httpProfile.setConnTimeout(10); // 连接超时：10 秒
            httpProfile.setReadTimeout(30); // 读取超时：30 秒（ASR处理较慢）

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            this.asrClient = new AsrClient(cred, region, clientProfile);
            logger.info("AsrClient 初始化完成，region={}", region);
        } catch (Exception e) {
            logger.error("AsrClient 初始化失败: {}", e.getMessage());
            // 不抛出异常，让 recognize() 方法处理
        }
    }

    /**
     * 语音识别
     * @param audioData 音频数据（Base64编码）
     * @param format 音频格式（webm, mp3等）
     * @param lang 语言（zh: 中文, en: 英语）
     * @return 识别结果文本
     */
    public String recognize(String audioData, String format, String lang) {
        if (asrClient == null) {
            throw new BusinessException("ASR 服务未配置");
        }

        if (audioData == null || audioData.isEmpty()) {
            throw new BusinessException("音频数据不能为空");
        }
        
        // 验证格式参数
        if (format == null || format.isEmpty()) {
            format = "webm";
        }
        if (lang == null || lang.isEmpty()) {
            lang = "zh";
        }

        long startTime = aiCallMetricsService != null ? aiCallMetricsService.startTimer() : 0;
        String user = AiCallMetricsService.getCurrentUser();

        try {
            SentenceRecognitionRequest req = new SentenceRecognitionRequest();
            req.setSourceType(1L);    // 1: 数据包传输
            req.setVoiceFormat(format);
            req.setData(audioData);

            // 语言代码映射：腾讯 ASR 使用 16k_zh, 16k_en 等
            String engineType = "16k_zh";
            if ("en".equalsIgnoreCase(lang)) {
                engineType = "16k_en";
            }
            req.setEngSerViceType(engineType);

            SentenceRecognitionResponse resp = asrClient.SentenceRecognition(req);
            
            // FIX: 防御性检查响应对象和结果字段
            if (resp == null) {
                logger.error("ASR 返回空响应对象");
                throw new BusinessException("ASR 识别失败，请稍后重试");
            }

            String result = resp.getResult();
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordSuccess("TENCENT_ASR", "recognize", startTime, user);
            }
            return result != null ? result : "";

        } catch (TencentCloudSDKException e) {
            // 不在日志中暴露错误详情，只记录错误码
            logger.error("ASR 识别失败 - 错误码: {}", e.getErrorCode());
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("TENCENT_ASR", "recognize", startTime,
                        e.getErrorCode(), user);
            }
            
            // 根据错误类型返回更友好的提示
            String errorCode = e.getErrorCode();
            if (errorCode != null && errorCode.contains("Unauthorized")) {
                throw new BusinessException("ASR服务权限不足，请检查腾讯云API密钥配置");
            } else if (errorCode != null && errorCode.contains("InvalidCredential")) {
                throw new BusinessException("ASR服务密钥无效，请检查配置");
            } else {
                throw new BusinessException("ASR 识别失败，请稍后重试");
            }
        } catch (Exception e) {
            // FIX: 捕获其他异常（如超时、网络错误等）
            logger.error("ASR 识别出错: {}", e.getMessage());
            if (aiCallMetricsService != null) {
                aiCallMetricsService.recordFailure("TENCENT_ASR", "recognize", startTime,
                        e.getMessage(), user);
            }
            throw new BusinessException("ASR 识别失败，请稍后重试");
        }
    }
}
