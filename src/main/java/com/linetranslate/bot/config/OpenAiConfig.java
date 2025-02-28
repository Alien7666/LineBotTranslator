package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.theokanning.openai.service.OpenAiService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Configuration
@Getter
@Slf4j
public class OpenAiConfig {

    @Value("${openai.api.key:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${openai.model.name:${OPENAI_MODEL_NAME:gpt-4o}}")
    private String modelName;

    @Value("${openai.api.url:${OPENAI_API_URL:https://api.openai.com/v1/chat/completions}}")
    private String apiUrl;

    @Bean(name = "openAiClient")
    public OpenAiService openAiClient() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("OpenAI API 金鑰未設置");
            // 返回一個空的服務，後續需要做好空值檢查
            return null;
        }

        log.info("初始化 OpenAI 服務, 使用模型: {}", modelName);
        return new OpenAiService(apiKey, Duration.ofSeconds(60));
    }
}