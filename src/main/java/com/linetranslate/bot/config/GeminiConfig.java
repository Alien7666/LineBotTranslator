package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

@Configuration
@Getter
@Slf4j
public class GeminiConfig {

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${gemini.model.name:${GEMINI_MODEL_NAME:gemini-1.5-pro}}")
    private String modelName;

    @Value("${gemini.available.models:${GEMINI_AVAILABLE_MODELS:gemini-1.5-pro,gemini-1.5-flash-001}}")
    private String availableModelsString;

    private List<String> availableModels;

    public GeminiConfig() {
        log.info("初始化 Gemini 配置");
    }

    /**
     * 獲取可用模型列表
     * @return 可用模型列表
     */
    public List<String> getAvailableModels() {
        if (availableModels == null) {
            log.info("讀取 Gemini 可用模型字符串: {}", availableModelsString);
            availableModels = Arrays.asList(availableModelsString.split(","));
            log.info("解析後的 Gemini 可用模型列表: {}", availableModels);
        }
        return availableModels;
    }
}