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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class TencentAsrService {

    private static final Logger logger = LoggerFactory.getLogger(TencentAsrService.class);

    @Value("${tencent.translate.secret-id:}")
    private String secretId;

    @Value("${tencent.translate.secret-key:}")
    private String secretKey;

    @Value("${tencent.translate.region:ap-guangzhou}")
    private String region;

    private AsrClient asrClient;

    @PostConstruct
    public void init() {
        if (secretId == null || secretId.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            logger.warn("腾讯 ASR secretId/secretKey 未配置，AsrClient 将不会初始化");
            return;
        }
        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("asr.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        this.asrClient = new AsrClient(cred, region, clientProfile);
        logger.info("AsrClient 初始化完成");
    }

    public String recognize(String audioData, String format, String lang) {
        if (asrClient == null) {
            throw new BusinessException("ASR 服务未配置");
        }

        if (audioData == null || audioData.isEmpty()) {
            throw new BusinessException("音频数据不能为空");
        }

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
            if (resp == null) {
                logger.error("ASR response is null");
                throw new BusinessException("ASR 识别失败，请稍后重试");
            }

            String result = resp.getResult();
            return result != null ? result : "";

        } catch (TencentCloudSDKException e) {
            logger.error("ASR 识别失败", e);
            throw new BusinessException("ASR 识别失败，请稍后重试");
        }
    }
}
