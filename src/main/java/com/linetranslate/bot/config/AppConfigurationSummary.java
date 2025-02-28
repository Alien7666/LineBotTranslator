package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.linetranslate.bot.service.ai.AiService;
import com.linetranslate.bot.service.ai.AiServiceFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Component
@Slf4j
public class AppConfigurationSummary implements ApplicationListener<ApplicationStartedEvent> {

    @Value("${line.bot.channel-token:未設置}")
    private String lineBotToken;

    @Value("${mongodb.uri:未設置}")
    private String mongodbUri;

    @Value("${app.ocr.enabled:false}")
    private boolean ocrEnabled;

    @Value("${app.ai.default-provider:openai}")
    private String defaultAiProvider;

    private final Optional<ImageAnnotatorClient> visionClient;
    private final AiServiceFactory aiServiceFactory;

    @Autowired
    public AppConfigurationSummary(
            @Autowired(required = false) ImageAnnotatorClient visionClient,
            AiServiceFactory aiServiceFactory) {
        this.visionClient = Optional.ofNullable(visionClient);
        this.aiServiceFactory = aiServiceFactory;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("---------------------------------------------");
        log.info("LINE Bot 翻譯機器人啟動完成");
        log.info("---------------------------------------------");
        log.info("LINE Bot Token: {}", isConfigured(lineBotToken) ? "已配置" : "未配置");
        log.info("MongoDB: {}", isConfigured(mongodbUri) ? "已連接" : "未連接");
        log.info("OCR 功能: {}", ocrEnabled ? (visionClient.isPresent() ? "已啟用" : "配置不完整") : "已禁用");
        log.info("預設 AI 提供者: {}", defaultAiProvider);

        try {
            AiService openAi = aiServiceFactory.getService("openai");
            AiService gemini = aiServiceFactory.getService("gemini");

            log.info("可用 AI 模型: ");

            // 檢查 OpenAI 服務是否可用
            if (!"fallback".equals(openAi.getProviderName())) {
                log.info("  - OpenAI: {}", openAi.getModelName());
            } else {
                log.info("  - OpenAI: 未配置");
            }

            // 檢查 Gemini 服務是否可用
            if (!"fallback".equals(gemini.getProviderName())) {
                log.info("  - Gemini: {}", gemini.getModelName());
            } else {
                log.info("  - Gemini: 未配置");
            }
        } catch (Exception e) {
            log.warn("無法獲取 AI 模型資訊: {}", e.getMessage());
        }

        log.info("---------------------------------------------");
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isEmpty() && !value.equals("未設置");
    }
}