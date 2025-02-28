package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Getter
@Slf4j
public class GeminiConfig {

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${gemini.model.name:${GEMINI_MODEL_NAME:gemini-1.5-pro}}")
    private String modelName;

    public GeminiConfig() {
        log.info("初始化 Gemini 配置");
    }
}