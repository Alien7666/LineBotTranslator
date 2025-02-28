package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Configuration
@Getter
@Slf4j
public class GoogleVisionConfig {

    @Value("${google.cloud.vision.api.key:#{null}}")
    private String apiKey;

    @Bean
    public ImageAnnotatorClient imageAnnotatorClient() {
        try {
            // 檢查 GOOGLE_APPLICATION_CREDENTIALS 環境變數
            String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credentialsPath != null && !credentialsPath.isEmpty()) {
                log.info("使用服務帳戶認證文件: {}", credentialsPath);
                return ImageAnnotatorClient.create();
            } else {
                log.warn("未找到 Google Vision API 認證文件，OCR 功能將不可用");
                return null;
            }
        } catch (IOException e) {
            log.error("無法創建 Google Vision 客戶端: {}", e.getMessage());
            return null;
        }
    }
}